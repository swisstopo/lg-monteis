package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_ENTRIES;
import static java.util.stream.Collectors.groupingBy;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumAttachment;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryWithDocuments;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  public List<FulcrumEntryWithDocuments> findAllWithDocuments() {
    Map<UUID, List<FulcrumDocument>> documentsByEntry =
        dsl.selectFrom(FULCRUM_DOCUMENTS).fetch(FulcrumRecordMapping::toDocument).stream()
            .collect(groupingBy(FulcrumDocument::entryId));
    return dsl
        .selectFrom(FULCRUM_ENTRIES)
        .orderBy(
            FULCRUM_ENTRIES.NUMBER.desc().nullsLast(), FULCRUM_ENTRIES.TITLE, FULCRUM_ENTRIES.ID)
        .fetch(FulcrumRecordMapping::toEntry)
        .stream()
        .map(
            entry ->
                new FulcrumEntryWithDocuments(
                    entry,
                    inAttachmentOrder(entry, documentsByEntry.getOrDefault(entry.id(), List.of()))))
        .toList();
  }

  @Override
  @Transactional
  public void saveSeen(List<FulcrumEntry> entries, OffsetDateTime seenAt) {
    List<Query> upserts = entries.stream().map(entry -> upsert(entry, seenAt)).toList();
    if (!upserts.isEmpty()) {
      dsl.batch(upserts).execute();
    }
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
  public List<FulcrumAttachment> findAttachmentsToStore() {
    Set<UUID> stored =
        dsl.select(FULCRUM_DOCUMENTS.ID).from(FULCRUM_DOCUMENTS).fetchSet(FULCRUM_DOCUMENTS.ID);
    return dsl
        .selectFrom(FULCRUM_ENTRIES)
        .where(FULCRUM_ENTRIES.REMOVED_AT.isNull())
        .orderBy(FULCRUM_ENTRIES.ID)
        .fetch(FulcrumRecordMapping::toEntry)
        .stream()
        .flatMap(entry -> entry.attachments().stream())
        .filter(attachment -> !stored.contains(attachment.id()))
        .toList();
  }

  private Query upsert(FulcrumEntry entry, OffsetDateTime seenAt) {
    UUID[] attachmentIds =
        entry.attachments().stream().map(FulcrumAttachment::id).toArray(UUID[]::new);
    String[] attachmentNames =
        entry.attachments().stream().map(FulcrumAttachment::fileName).toArray(String[]::new);
    return dsl.insertInto(FULCRUM_ENTRIES)
        .set(FULCRUM_ENTRIES.ID, entry.id())
        .set(FULCRUM_ENTRIES.NUMBER, entry.number())
        .set(FULCRUM_ENTRIES.TITLE, entry.title())
        .set(FULCRUM_ENTRIES.AUTHORS, entry.authors())
        .set(FULCRUM_ENTRIES.YEAR, entry.year())
        .set(FULCRUM_ENTRIES.CATEGORY, entry.category())
        .set(FULCRUM_ENTRIES.KEYWORDS, entry.keywords().toArray(String[]::new))
        .set(FULCRUM_ENTRIES.ATTACHMENT_IDS, attachmentIds)
        .set(FULCRUM_ENTRIES.ATTACHMENT_NAMES, attachmentNames)
        .set(FULCRUM_ENTRIES.LAST_SEEN_AT, seenAt)
        .onConflict(FULCRUM_ENTRIES.ID)
        .doUpdate()
        .set(FULCRUM_ENTRIES.NUMBER, DSL.excluded(FULCRUM_ENTRIES.NUMBER))
        .set(FULCRUM_ENTRIES.TITLE, DSL.excluded(FULCRUM_ENTRIES.TITLE))
        .set(FULCRUM_ENTRIES.AUTHORS, DSL.excluded(FULCRUM_ENTRIES.AUTHORS))
        .set(FULCRUM_ENTRIES.YEAR, DSL.excluded(FULCRUM_ENTRIES.YEAR))
        .set(FULCRUM_ENTRIES.CATEGORY, DSL.excluded(FULCRUM_ENTRIES.CATEGORY))
        .set(FULCRUM_ENTRIES.KEYWORDS, DSL.excluded(FULCRUM_ENTRIES.KEYWORDS))
        .set(FULCRUM_ENTRIES.ATTACHMENT_IDS, DSL.excluded(FULCRUM_ENTRIES.ATTACHMENT_IDS))
        .set(FULCRUM_ENTRIES.ATTACHMENT_NAMES, DSL.excluded(FULCRUM_ENTRIES.ATTACHMENT_NAMES))
        .set(FULCRUM_ENTRIES.LAST_SEEN_AT, DSL.excluded(FULCRUM_ENTRIES.LAST_SEEN_AT))
        .setNull(FULCRUM_ENTRIES.REMOVED_AT);
  }

  // a document whose attachment the entry no longer lists goes last
  private static List<FulcrumDocument> inAttachmentOrder(
      FulcrumEntry entry, List<FulcrumDocument> documents) {
    List<UUID> order = entry.attachments().stream().map(FulcrumAttachment::id).toList();
    return documents.stream()
        .sorted(
            Comparator.comparingInt(
                document -> {
                  int position = order.indexOf(document.id());
                  return position < 0 ? Integer.MAX_VALUE : position;
                }))
        .toList();
  }
}
