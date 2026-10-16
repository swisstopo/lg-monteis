package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_ENTRIES;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqFulcrumDocumentRepository implements FulcrumDocumentRepository {

  private final DSLContext dsl;

  public JooqFulcrumDocumentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  @Transactional
  public FulcrumDocument create(UUID id, UUID entryId, DocumentMetadata metadata) {
    return dsl.insertInto(FULCRUM_DOCUMENTS)
        .set(FULCRUM_DOCUMENTS.ID, id)
        .set(FULCRUM_DOCUMENTS.ENTRY_ID, entryId)
        .set(FULCRUM_DOCUMENTS.FILE_NAME, metadata.fileName())
        .set(FULCRUM_DOCUMENTS.CONTENT_TYPE, metadata.contentType())
        .set(FULCRUM_DOCUMENTS.SIZE_BYTES, metadata.sizeBytes())
        .returning()
        .fetchSingle(FulcrumRecordMapping::toDocument);
  }

  @Override
  @Transactional(readOnly = true)
  public FulcrumDocument getById(UUID id) {
    return dsl.selectFrom(FULCRUM_DOCUMENTS)
        .where(FULCRUM_DOCUMENTS.ID.eq(id))
        .fetchOptional(FulcrumRecordMapping::toDocument)
        .orElseThrow(() -> new ObjectNotFoundException(FulcrumDocument.class));
  }

  @Override
  @Transactional
  public void reconcileWithEntries() {
    dsl.update(FULCRUM_DOCUMENTS)
        .set(FULCRUM_DOCUMENTS.REMOVED_AT, DSL.currentOffsetDateTime())
        .where(FULCRUM_DOCUMENTS.REMOVED_AT.isNull())
        .andNotExists(DSL.selectOne().from(FULCRUM_ENTRIES).where(listsTheDocument()))
        .execute();
    dsl.update(FULCRUM_DOCUMENTS)
        .setNull(FULCRUM_DOCUMENTS.REMOVED_AT)
        .where(FULCRUM_DOCUMENTS.REMOVED_AT.isNotNull())
        .andExists(DSL.selectOne().from(FULCRUM_ENTRIES).where(listsTheDocument()))
        .execute();
    dsl.update(FULCRUM_DOCUMENTS)
        .set(
            FULCRUM_DOCUMENTS.ENTRY_ID,
            DSL.select(FULCRUM_ENTRIES.ID)
                .from(FULCRUM_ENTRIES)
                .where(listsTheDocument())
                .orderBy(FULCRUM_ENTRIES.ID)
                .limit(1))
        .whereExists(
            DSL.selectOne()
                .from(FULCRUM_ENTRIES)
                .where(listsTheDocument())
                .and(FULCRUM_ENTRIES.ID.ne(FULCRUM_DOCUMENTS.ENTRY_ID)))
        .andNotExists(
            DSL.selectOne()
                .from(FULCRUM_ENTRIES)
                .where(listsTheDocument())
                .and(FULCRUM_ENTRIES.ID.eq(FULCRUM_DOCUMENTS.ENTRY_ID)))
        .execute();
  }

  // an entry still in Fulcrum lists the attachment of the outer document row
  private static Condition listsTheDocument() {
    return FULCRUM_ENTRIES
        .REMOVED_AT
        .isNull()
        .and(FULCRUM_DOCUMENTS.ID.eq(DSL.any(FULCRUM_ENTRIES.ATTACHMENT_IDS)));
  }
}
