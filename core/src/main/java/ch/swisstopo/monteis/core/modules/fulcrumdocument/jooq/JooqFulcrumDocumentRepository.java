package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENT_FILES;
import static java.util.stream.Collectors.groupingBy;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumDocumentFilesRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumDocumentsRecord;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Query;
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
  @Transactional(readOnly = true)
  public List<FulcrumDocument> findAll() {
    Map<UUID, List<FulcrumDocumentFile>> filesByDocument =
        dsl
            .selectFrom(FULCRUM_DOCUMENT_FILES)
            .orderBy(FULCRUM_DOCUMENT_FILES.DOCUMENT_ID, FULCRUM_DOCUMENT_FILES.POSITION)
            .fetch(JooqFulcrumDocumentRepository::toFile)
            .stream()
            .collect(groupingBy(FulcrumDocumentFile::documentId));
    return dsl.selectFrom(FULCRUM_DOCUMENTS)
        .orderBy(
            FULCRUM_DOCUMENTS.NUMBER.desc().nullsLast(),
            FULCRUM_DOCUMENTS.TITLE,
            FULCRUM_DOCUMENTS.ID)
        .fetch(
            documentRecord ->
                toDocument(
                    documentRecord,
                    filesByDocument.getOrDefault(documentRecord.getId(), List.of())));
  }

  @Override
  @Transactional(readOnly = true)
  public FulcrumDocumentFile getFileById(UUID fileId) {
    return dsl.selectFrom(FULCRUM_DOCUMENT_FILES)
        .where(FULCRUM_DOCUMENT_FILES.ID.eq(fileId))
        .fetchOptional(JooqFulcrumDocumentRepository::toFile)
        .orElseThrow(() -> new ObjectNotFoundException(FulcrumDocumentFile.class));
  }

  @Override
  @Transactional
  public void saveSeen(List<FulcrumDocument> documents, OffsetDateTime seenAt) {
    List<Query> upserts =
        documents.stream()
            .flatMap(
                document ->
                    Stream.concat(
                        Stream.of(upsertDocument(document, seenAt)),
                        IntStream.range(0, document.files().size())
                            .mapToObj(
                                position ->
                                    upsertFile(document.files().get(position), position, seenAt))))
            .toList();
    if (!upserts.isEmpty()) {
      dsl.batch(upserts).execute();
    }
  }

  @Override
  @Transactional
  public int markRemovedUnlessSeenAt(OffsetDateTime seenAt) {
    dsl.update(FULCRUM_DOCUMENT_FILES)
        .set(FULCRUM_DOCUMENT_FILES.REMOVED_AT, DSL.currentOffsetDateTime())
        .where(FULCRUM_DOCUMENT_FILES.REMOVED_AT.isNull())
        .and(FULCRUM_DOCUMENT_FILES.LAST_SEEN_AT.lt(seenAt))
        .execute();
    return dsl.update(FULCRUM_DOCUMENTS)
        .set(FULCRUM_DOCUMENTS.REMOVED_AT, DSL.currentOffsetDateTime())
        .where(FULCRUM_DOCUMENTS.REMOVED_AT.isNull())
        .and(FULCRUM_DOCUMENTS.LAST_SEEN_AT.lt(seenAt))
        .execute();
  }

  @Override
  @Transactional(readOnly = true)
  public List<FulcrumDocumentFile> findFilesToStore() {
    return dsl.selectFrom(FULCRUM_DOCUMENT_FILES)
        .where(FULCRUM_DOCUMENT_FILES.STORED_AT.isNull())
        .and(FULCRUM_DOCUMENT_FILES.REMOVED_AT.isNull())
        .orderBy(FULCRUM_DOCUMENT_FILES.DOCUMENT_ID, FULCRUM_DOCUMENT_FILES.POSITION)
        .fetch(JooqFulcrumDocumentRepository::toFile);
  }

  @Override
  @Transactional
  public void markStored(UUID fileId, long sizeBytes) {
    dsl.update(FULCRUM_DOCUMENT_FILES)
        .set(FULCRUM_DOCUMENT_FILES.SIZE_BYTES, sizeBytes)
        .set(FULCRUM_DOCUMENT_FILES.STORED_AT, DSL.currentOffsetDateTime())
        .where(FULCRUM_DOCUMENT_FILES.ID.eq(fileId))
        .execute();
  }

  private Query upsertDocument(FulcrumDocument document, OffsetDateTime seenAt) {
    return dsl.insertInto(FULCRUM_DOCUMENTS)
        .set(FULCRUM_DOCUMENTS.ID, document.id())
        .set(FULCRUM_DOCUMENTS.NUMBER, document.number())
        .set(FULCRUM_DOCUMENTS.TITLE, document.title())
        .set(FULCRUM_DOCUMENTS.AUTHORS, document.authors())
        .set(FULCRUM_DOCUMENTS.YEAR, document.year())
        .set(FULCRUM_DOCUMENTS.CATEGORY, document.category())
        .set(FULCRUM_DOCUMENTS.KEYWORDS, document.keywords().toArray(String[]::new))
        .set(FULCRUM_DOCUMENTS.LAST_SEEN_AT, seenAt)
        .onConflict(FULCRUM_DOCUMENTS.ID)
        .doUpdate()
        .set(FULCRUM_DOCUMENTS.NUMBER, DSL.excluded(FULCRUM_DOCUMENTS.NUMBER))
        .set(FULCRUM_DOCUMENTS.TITLE, DSL.excluded(FULCRUM_DOCUMENTS.TITLE))
        .set(FULCRUM_DOCUMENTS.AUTHORS, DSL.excluded(FULCRUM_DOCUMENTS.AUTHORS))
        .set(FULCRUM_DOCUMENTS.YEAR, DSL.excluded(FULCRUM_DOCUMENTS.YEAR))
        .set(FULCRUM_DOCUMENTS.CATEGORY, DSL.excluded(FULCRUM_DOCUMENTS.CATEGORY))
        .set(FULCRUM_DOCUMENTS.KEYWORDS, DSL.excluded(FULCRUM_DOCUMENTS.KEYWORDS))
        .set(FULCRUM_DOCUMENTS.LAST_SEEN_AT, DSL.excluded(FULCRUM_DOCUMENTS.LAST_SEEN_AT))
        .setNull(FULCRUM_DOCUMENTS.REMOVED_AT);
  }

  // an attachment Fulcrum moved to another entry follows it, its stored content stays valid
  private Query upsertFile(FulcrumDocumentFile file, int position, OffsetDateTime seenAt) {
    return dsl.insertInto(FULCRUM_DOCUMENT_FILES)
        .set(FULCRUM_DOCUMENT_FILES.ID, file.id())
        .set(FULCRUM_DOCUMENT_FILES.DOCUMENT_ID, file.documentId())
        .set(FULCRUM_DOCUMENT_FILES.POSITION, position)
        .set(FULCRUM_DOCUMENT_FILES.FILE_NAME, file.fileName())
        .set(FULCRUM_DOCUMENT_FILES.CONTENT_TYPE, file.contentType())
        .set(FULCRUM_DOCUMENT_FILES.LAST_SEEN_AT, seenAt)
        .onConflict(FULCRUM_DOCUMENT_FILES.ID)
        .doUpdate()
        .set(FULCRUM_DOCUMENT_FILES.DOCUMENT_ID, DSL.excluded(FULCRUM_DOCUMENT_FILES.DOCUMENT_ID))
        .set(FULCRUM_DOCUMENT_FILES.POSITION, DSL.excluded(FULCRUM_DOCUMENT_FILES.POSITION))
        .set(FULCRUM_DOCUMENT_FILES.FILE_NAME, DSL.excluded(FULCRUM_DOCUMENT_FILES.FILE_NAME))
        .set(FULCRUM_DOCUMENT_FILES.CONTENT_TYPE, DSL.excluded(FULCRUM_DOCUMENT_FILES.CONTENT_TYPE))
        .set(FULCRUM_DOCUMENT_FILES.LAST_SEEN_AT, DSL.excluded(FULCRUM_DOCUMENT_FILES.LAST_SEEN_AT))
        .setNull(FULCRUM_DOCUMENT_FILES.REMOVED_AT);
  }

  private static FulcrumDocument toDocument(
      FulcrumDocumentsRecord documentRecord, List<FulcrumDocumentFile> files) {
    String[] keywords = documentRecord.getKeywords();
    return new FulcrumDocument(
        documentRecord.getId(),
        documentRecord.getNumber(),
        documentRecord.getTitle(),
        documentRecord.getAuthors(),
        documentRecord.getYear(),
        documentRecord.getCategory(),
        keywords == null ? List.of() : List.of(keywords),
        documentRecord.getRemovedAt(),
        files);
  }

  private static FulcrumDocumentFile toFile(FulcrumDocumentFilesRecord fileRecord) {
    return new FulcrumDocumentFile(
        fileRecord.getId(),
        fileRecord.getDocumentId(),
        fileRecord.getFileName(),
        fileRecord.getContentType(),
        fileRecord.getSizeBytes(),
        fileRecord.getStoredAt(),
        fileRecord.getRemovedAt());
  }
}
