package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENTS;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqFulcrumDocumentRepository implements FulcrumDocumentRepository {

  private final DSLContext dsl;
  private final FulcrumDocumentJooqMapper mapper;

  public JooqFulcrumDocumentRepository(DSLContext dsl, FulcrumDocumentJooqMapper mapper) {
    this.dsl = dsl;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<FulcrumDocument> findAll() {
    return dsl.selectFrom(FULCRUM_DOCUMENTS)
        .orderBy(
            FULCRUM_DOCUMENTS.NUMBER.desc().nullsLast(),
            FULCRUM_DOCUMENTS.TITLE,
            FULCRUM_DOCUMENTS.ID)
        .fetch(mapper::toDocument);
  }

  @Override
  @Transactional(readOnly = true)
  public FulcrumDocument getById(UUID id) {
    return dsl.selectFrom(FULCRUM_DOCUMENTS)
        .where(FULCRUM_DOCUMENTS.ID.eq(id))
        .fetchOptional(mapper::toDocument)
        .orElseThrow(() -> new ObjectNotFoundException(FulcrumDocument.class));
  }

  @Override
  @Transactional
  public void saveSynced(List<FulcrumDocument> documents, OffsetDateTime syncedAt) {
    List<Query> upserts = documents.stream().map(document -> upsert(document, syncedAt)).toList();
    if (!upserts.isEmpty()) {
      dsl.batch(upserts).execute();
    }
  }

  @Override
  @Transactional
  public void markRemovedFromFulcrum(Collection<UUID> documentIds) {
    if (documentIds.isEmpty()) {
      return;
    }
    dsl.update(FULCRUM_DOCUMENTS)
        .set(FULCRUM_DOCUMENTS.REMOVED_AT, DSL.currentOffsetDateTime())
        .where(FULCRUM_DOCUMENTS.ID.in(documentIds))
        .and(FULCRUM_DOCUMENTS.REMOVED_AT.isNull())
        .execute();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OffsetDateTime> findLastSuccessfulSyncAt() {
    // max over no rows is one row holding null
    return Optional.ofNullable(
        dsl.select(DSL.max(FULCRUM_DOCUMENTS.LAST_SYNCED_AT))
            .from(FULCRUM_DOCUMENTS)
            .fetchSingle()
            .value1());
  }

  // stored_at only moves when the row switches to another file
  private Query upsert(FulcrumDocument document, OffsetDateTime syncedAt) {
    DocumentMetadata metadata = document.file().metadata();
    return dsl.insertInto(FULCRUM_DOCUMENTS)
        .set(FULCRUM_DOCUMENTS.ID, document.id())
        .set(FULCRUM_DOCUMENTS.NUMBER, document.number())
        .set(FULCRUM_DOCUMENTS.TITLE, document.title())
        .set(FULCRUM_DOCUMENTS.AUTHORS, document.authors())
        .set(FULCRUM_DOCUMENTS.YEAR, document.year())
        .set(FULCRUM_DOCUMENTS.CATEGORY, document.category())
        .set(FULCRUM_DOCUMENTS.KEYWORDS, document.keywords().toArray(String[]::new))
        .set(FULCRUM_DOCUMENTS.ATTACHMENT_ID, document.file().attachmentId())
        .set(FULCRUM_DOCUMENTS.FILE_NAME, metadata.fileName())
        .set(FULCRUM_DOCUMENTS.CONTENT_TYPE, metadata.contentType())
        .set(FULCRUM_DOCUMENTS.SIZE_BYTES, metadata.sizeBytes())
        .set(FULCRUM_DOCUMENTS.LAST_SYNCED_AT, syncedAt)
        .onConflict(FULCRUM_DOCUMENTS.ID)
        .doUpdate()
        .set(FULCRUM_DOCUMENTS.NUMBER, DSL.excluded(FULCRUM_DOCUMENTS.NUMBER))
        .set(FULCRUM_DOCUMENTS.TITLE, DSL.excluded(FULCRUM_DOCUMENTS.TITLE))
        .set(FULCRUM_DOCUMENTS.AUTHORS, DSL.excluded(FULCRUM_DOCUMENTS.AUTHORS))
        .set(FULCRUM_DOCUMENTS.YEAR, DSL.excluded(FULCRUM_DOCUMENTS.YEAR))
        .set(FULCRUM_DOCUMENTS.CATEGORY, DSL.excluded(FULCRUM_DOCUMENTS.CATEGORY))
        .set(FULCRUM_DOCUMENTS.KEYWORDS, DSL.excluded(FULCRUM_DOCUMENTS.KEYWORDS))
        .set(
            FULCRUM_DOCUMENTS.STORED_AT,
            DSL.when(
                    FULCRUM_DOCUMENTS.ATTACHMENT_ID.ne(
                        DSL.excluded(FULCRUM_DOCUMENTS.ATTACHMENT_ID)),
                    DSL.currentOffsetDateTime())
                .otherwise(FULCRUM_DOCUMENTS.STORED_AT))
        .set(FULCRUM_DOCUMENTS.ATTACHMENT_ID, DSL.excluded(FULCRUM_DOCUMENTS.ATTACHMENT_ID))
        .set(FULCRUM_DOCUMENTS.FILE_NAME, DSL.excluded(FULCRUM_DOCUMENTS.FILE_NAME))
        .set(FULCRUM_DOCUMENTS.CONTENT_TYPE, DSL.excluded(FULCRUM_DOCUMENTS.CONTENT_TYPE))
        .set(FULCRUM_DOCUMENTS.SIZE_BYTES, DSL.excluded(FULCRUM_DOCUMENTS.SIZE_BYTES))
        .set(FULCRUM_DOCUMENTS.LAST_SYNCED_AT, DSL.excluded(FULCRUM_DOCUMENTS.LAST_SYNCED_AT))
        .setNull(FULCRUM_DOCUMENTS.REMOVED_AT);
  }
}
