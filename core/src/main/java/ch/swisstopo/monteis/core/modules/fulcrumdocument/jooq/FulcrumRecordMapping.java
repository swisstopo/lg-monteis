package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumDocumentsRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumEntriesRecord;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumAttachment;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

final class FulcrumRecordMapping {

  private FulcrumRecordMapping() {}

  static FulcrumEntry toEntry(FulcrumEntriesRecord entryRecord) {
    String[] keywords = entryRecord.getKeywords();
    return new FulcrumEntry(
        entryRecord.getId(),
        entryRecord.getNumber(),
        entryRecord.getTitle(),
        entryRecord.getAuthors(),
        entryRecord.getYear(),
        entryRecord.getCategory(),
        keywords == null ? List.of() : List.of(keywords),
        attachmentsOf(entryRecord),
        entryRecord.getRemovedAt());
  }

  static FulcrumDocument toDocument(FulcrumDocumentsRecord documentRecord) {
    return new FulcrumDocument(
        documentRecord.getId(),
        documentRecord.getEntryId(),
        new DocumentMetadata(
            documentRecord.getFileName(),
            documentRecord.getContentType(),
            documentRecord.getSizeBytes()),
        documentRecord.getStoredAt(),
        documentRecord.getRemovedAt());
  }

  private static List<FulcrumAttachment> attachmentsOf(FulcrumEntriesRecord entryRecord) {
    UUID[] ids = entryRecord.getAttachmentIds();
    String[] names = entryRecord.getAttachmentNames();
    return IntStream.range(0, ids.length)
        .mapToObj(i -> new FulcrumAttachment(ids[i], entryRecord.getId(), names[i]))
        .toList();
  }
}
