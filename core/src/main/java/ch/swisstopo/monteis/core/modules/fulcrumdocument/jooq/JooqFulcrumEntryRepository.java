package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_ENTRIES;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumEntriesRecord;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryDetails;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqFulcrumEntryRepository implements FulcrumEntryRepository {

  private final DSLContext dsl;

  public JooqFulcrumEntryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  @Transactional(readOnly = true)
  public List<FulcrumEntry> findAll() {
    return dsl.selectFrom(FULCRUM_ENTRIES)
        .orderBy(
            FULCRUM_ENTRIES.NUMBER.desc().nullsLast(), FULCRUM_ENTRIES.TITLE, FULCRUM_ENTRIES.ID)
        .fetch(JooqFulcrumEntryRepository::toEntry);
  }

  // Fulcrum may have moved the attachment to another entry, any of them holds the same content
  @Override
  @Transactional(readOnly = true)
  public FulcrumDocument getDocumentById(UUID documentId) {
    return dsl.selectFrom(FULCRUM_ENTRIES)
        .where(FULCRUM_ENTRIES.ATTACHMENT_ID.eq(documentId))
        .limit(1)
        .fetchOptional(JooqFulcrumEntryRepository::toDocument)
        .orElseThrow(() -> new ObjectNotFoundException(FulcrumDocument.class));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UUID> findDocumentIdsByEntry() {
    return dsl.select(FULCRUM_ENTRIES.ID, FULCRUM_ENTRIES.ATTACHMENT_ID)
        .from(FULCRUM_ENTRIES)
        .fetchMap(FULCRUM_ENTRIES.ID, FULCRUM_ENTRIES.ATTACHMENT_ID);
  }

  @Override
  @Transactional
  public void saveSeenDetails(List<FulcrumEntryDetails> details, OffsetDateTime seenAt) {
    List<Query> updates =
        details.stream()
            .map(
                entry ->
                    (Query)
                        dsl.update(FULCRUM_ENTRIES)
                            .set(FULCRUM_ENTRIES.NUMBER, entry.number())
                            .set(FULCRUM_ENTRIES.TITLE, entry.title())
                            .set(FULCRUM_ENTRIES.AUTHORS, entry.authors())
                            .set(FULCRUM_ENTRIES.YEAR, entry.year())
                            .set(FULCRUM_ENTRIES.CATEGORY, entry.category())
                            .set(FULCRUM_ENTRIES.KEYWORDS, entry.keywords().toArray(String[]::new))
                            .set(FULCRUM_ENTRIES.LAST_SEEN_AT, seenAt)
                            .setNull(FULCRUM_ENTRIES.REMOVED_AT)
                            .where(FULCRUM_ENTRIES.ID.eq(entry.id())))
            .toList();
    if (!updates.isEmpty()) {
      dsl.batch(updates).execute();
    }
  }

  @Override
  @Transactional
  public void saveWithDocument(
      FulcrumEntryDetails details, FulcrumDocument document, OffsetDateTime seenAt) {
    DocumentMetadata metadata = document.metadata();
    dsl.insertInto(FULCRUM_ENTRIES)
        .set(FULCRUM_ENTRIES.ID, details.id())
        .set(FULCRUM_ENTRIES.NUMBER, details.number())
        .set(FULCRUM_ENTRIES.TITLE, details.title())
        .set(FULCRUM_ENTRIES.AUTHORS, details.authors())
        .set(FULCRUM_ENTRIES.YEAR, details.year())
        .set(FULCRUM_ENTRIES.CATEGORY, details.category())
        .set(FULCRUM_ENTRIES.KEYWORDS, details.keywords().toArray(String[]::new))
        .set(FULCRUM_ENTRIES.ATTACHMENT_ID, document.id())
        .set(FULCRUM_ENTRIES.FILE_NAME, metadata.fileName())
        .set(FULCRUM_ENTRIES.CONTENT_TYPE, metadata.contentType())
        .set(FULCRUM_ENTRIES.SIZE_BYTES, metadata.sizeBytes())
        .set(FULCRUM_ENTRIES.LAST_SEEN_AT, seenAt)
        .onConflict(FULCRUM_ENTRIES.ID)
        .doUpdate()
        .set(FULCRUM_ENTRIES.NUMBER, DSL.excluded(FULCRUM_ENTRIES.NUMBER))
        .set(FULCRUM_ENTRIES.TITLE, DSL.excluded(FULCRUM_ENTRIES.TITLE))
        .set(FULCRUM_ENTRIES.AUTHORS, DSL.excluded(FULCRUM_ENTRIES.AUTHORS))
        .set(FULCRUM_ENTRIES.YEAR, DSL.excluded(FULCRUM_ENTRIES.YEAR))
        .set(FULCRUM_ENTRIES.CATEGORY, DSL.excluded(FULCRUM_ENTRIES.CATEGORY))
        .set(FULCRUM_ENTRIES.KEYWORDS, DSL.excluded(FULCRUM_ENTRIES.KEYWORDS))
        .set(FULCRUM_ENTRIES.ATTACHMENT_ID, DSL.excluded(FULCRUM_ENTRIES.ATTACHMENT_ID))
        .set(FULCRUM_ENTRIES.FILE_NAME, DSL.excluded(FULCRUM_ENTRIES.FILE_NAME))
        .set(FULCRUM_ENTRIES.CONTENT_TYPE, DSL.excluded(FULCRUM_ENTRIES.CONTENT_TYPE))
        .set(FULCRUM_ENTRIES.SIZE_BYTES, DSL.excluded(FULCRUM_ENTRIES.SIZE_BYTES))
        .set(FULCRUM_ENTRIES.STORED_AT, DSL.currentOffsetDateTime())
        .set(FULCRUM_ENTRIES.LAST_SEEN_AT, DSL.excluded(FULCRUM_ENTRIES.LAST_SEEN_AT))
        .setNull(FULCRUM_ENTRIES.REMOVED_AT)
        .execute();
  }

  @Override
  @Transactional
  public int markRemovedUnlessSeenAt(OffsetDateTime seenAt) {
    return dsl.update(FULCRUM_ENTRIES)
        .set(FULCRUM_ENTRIES.REMOVED_AT, DSL.currentOffsetDateTime())
        .where(FULCRUM_ENTRIES.REMOVED_AT.isNull())
        .and(FULCRUM_ENTRIES.LAST_SEEN_AT.lt(seenAt))
        .execute();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OffsetDateTime> findLastSeenAt() {
    return Optional.ofNullable(
        dsl.select(DSL.max(FULCRUM_ENTRIES.LAST_SEEN_AT))
            .from(FULCRUM_ENTRIES)
            .fetchOne()
            .value1());
  }

  private static FulcrumEntry toEntry(FulcrumEntriesRecord entryRecord) {
    String[] keywords = entryRecord.getKeywords();
    return new FulcrumEntry(
        new FulcrumEntryDetails(
            entryRecord.getId(),
            entryRecord.getNumber(),
            entryRecord.getTitle(),
            entryRecord.getAuthors(),
            entryRecord.getYear(),
            entryRecord.getCategory(),
            keywords == null ? List.of() : List.of(keywords)),
        toDocument(entryRecord),
        entryRecord.getRemovedAt());
  }

  private static FulcrumDocument toDocument(FulcrumEntriesRecord entryRecord) {
    return new FulcrumDocument(
        entryRecord.getAttachmentId(),
        new DocumentMetadata(
            entryRecord.getFileName(), entryRecord.getContentType(), entryRecord.getSizeBytes()));
  }
}
