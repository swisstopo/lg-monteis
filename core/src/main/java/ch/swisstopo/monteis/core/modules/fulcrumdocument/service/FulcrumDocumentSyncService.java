package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentTooLargeException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// mirrors the Fulcrum literature into MONTEIS (ADR "Fulcrum Documents spiegeln statt On-Demand
// abrufen"). no transaction around a sync, it would hold a db connection for minutes
@Service
public class FulcrumDocumentSyncService {

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentSyncService.class);

  private final FulcrumService fulcrumService;
  private final FulcrumDocumentImportService importService;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumSyncRepository syncRepository;
  private final FulcrumLiteratureMapper literatureMapper;
  private final Clock clock;

  public FulcrumDocumentSyncService(
      FulcrumService fulcrumService,
      FulcrumDocumentImportService importService,
      FulcrumDocumentRepository documentRepository,
      FulcrumSyncRepository syncRepository,
      FulcrumLiteratureMapper literatureMapper,
      Clock clock) {
    this.fulcrumService = fulcrumService;
    this.importService = importService;
    this.documentRepository = documentRepository;
    this.syncRepository = syncRepository;
    this.literatureMapper = literatureMapper;
    this.clock = clock;
  }

  // files not imported by the deadline are left to the next sync
  public void sync(Instant deadline) {
    Instant start = clock.instant();
    if (syncFrom(start, deadline).isSuccessful()) {
      syncRepository.saveSuccessfulSync(start.atOffset(ZoneOffset.UTC));
    }
  }

  private SyncOutcome syncFrom(Instant start, Instant deadline) {
    List<FulcrumLiteratureRow> fromFulcrum = fulcrumService.findLiteratureMarkedForMonteis();
    if (fromFulcrum.isEmpty()) {
      log.warn(
          "Fulcrum returned no literature at all, no Fulcrum Document is marked as removed and the"
              + " sync does not count as successful");
      return SyncOutcome.NOTHING_FROM_FULCRUM;
    }
    Map<UUID, FulcrumDocument> storedById = storedDocumentsById();

    int failedSaves = saveEachSynced(storedDocumentsAsFulcrumReturnedThem(fromFulcrum, storedById));
    List<UUID> noLongerInFulcrum = idsNoLongerInFulcrum(storedById, fromFulcrum);
    documentRepository.markRemovedFromFulcrum(noLongerInFulcrum, start.atOffset(ZoneOffset.UTC));
    ImportCounts imports = importNewFiles(rowsWithFileToImport(fromFulcrum, storedById), deadline);

    SyncOutcome outcome =
        new SyncOutcome(fromFulcrum.size(), failedSaves, noLongerInFulcrum.size(), imports);
    log.info(
        "Fulcrum Document sync: {} documents from Fulcrum, {} failed to save, {} newly removed,"
            + " {} files imported, {} skipped as too large, {} failed{}, successful: {}",
        outcome.fromFulcrum(),
        outcome.failedSaves(),
        outcome.newlyRemoved(),
        imports.imported(),
        imports.skipped(),
        imports.failed(),
        imports.outOfTime() ? ", ran out of time" : "",
        outcome.isSuccessful());
    return outcome;
  }

  private Map<UUID, FulcrumDocument> storedDocumentsById() {
    return documentRepository.findAll().stream()
        .collect(Collectors.toMap(FulcrumDocument::id, Function.identity()));
  }

  // a replaced file is saved as synced too, the document keeps its old file until the new one is in
  private List<FulcrumDocument> storedDocumentsAsFulcrumReturnedThem(
      List<FulcrumLiteratureRow> fromFulcrum, Map<UUID, FulcrumDocument> storedById) {
    return fromFulcrum.stream()
        .filter(row -> storedById.containsKey(row.recordId()))
        .map(row -> literatureMapper.toDocument(row, storedById.get(row.recordId()).file()))
        .toList();
  }

  // one document that does not fit the table must not keep the others from being saved
  private int saveEachSynced(List<FulcrumDocument> documents) {
    int failed = 0;
    for (FulcrumDocument document : documents) {
      try {
        documentRepository.saveSynced(document);
      } catch (RuntimeException e) {
        failed++;
        log.warn("Could not save Fulcrum Document {}", document.id(), e);
      }
    }
    return failed;
  }

  // stored documents still available that Fulcrum did not return this time
  private static List<UUID> idsNoLongerInFulcrum(
      Map<UUID, FulcrumDocument> storedById, List<FulcrumLiteratureRow> fromFulcrum) {
    Set<UUID> inFulcrum =
        fromFulcrum.stream().map(FulcrumLiteratureRow::recordId).collect(Collectors.toSet());
    return storedById.values().stream()
        .filter(FulcrumDocument::isAvailableInFulcrum)
        .map(FulcrumDocument::id)
        .filter(id -> !inFulcrum.contains(id))
        .toList();
  }

  // a file too large is skipped on purpose and not counted as failed, it would fail every sync
  private ImportCounts importNewFiles(List<FulcrumLiteratureRow> rowsToImport, Instant deadline) {
    int imported = 0;
    int skipped = 0;
    int failed = 0;
    for (FulcrumLiteratureRow row : rowsToImport) {
      if (clock.instant().isAfter(deadline)) {
        log.info("Fulcrum Document sync ran out of time, the next one continues");
        return new ImportCounts(imported, skipped, failed, true);
      }
      try {
        importService.importFulcrumDocument(row);
        imported++;
      } catch (FulcrumAttachmentTooLargeException e) {
        skipped++;
        log.warn("Skipping the file of Fulcrum Document {}: {}", row.recordId(), e.getMessage());
      } catch (RuntimeException e) {
        failed++;
        log.warn("Could not import the file of Fulcrum Document {}", row.recordId(), e);
      }
    }
    return new ImportCounts(imported, skipped, failed, false);
  }

  // a new document, or a stored one whose file Fulcrum replaced
  private static List<FulcrumLiteratureRow> rowsWithFileToImport(
      List<FulcrumLiteratureRow> fromFulcrum, Map<UUID, FulcrumDocument> storedById) {
    return fromFulcrum.stream()
        .filter(row -> hasFileNotStoredYet(row, storedById.get(row.recordId())))
        .toList();
  }

  private static boolean hasFileNotStoredYet(
      FulcrumLiteratureRow row, @Nullable FulcrumDocument stored) {
    return row.firstAttachment()
        .map(attachment -> stored == null || !stored.file().attachmentId().equals(attachment.id()))
        .orElse(false);
  }

  private record ImportCounts(int imported, int skipped, int failed, boolean outOfTime) {}

  // successful: Fulcrum returned the literature, every document and file got in and the sync
  // finished within its time budget. a file skipped as too large does not count against it
  private record SyncOutcome(
      int fromFulcrum, int failedSaves, int newlyRemoved, ImportCounts imports) {

    static final SyncOutcome NOTHING_FROM_FULCRUM =
        new SyncOutcome(0, 0, 0, new ImportCounts(0, 0, 0, false));

    boolean isSuccessful() {
      return fromFulcrum > 0 && failedSaves == 0 && imports.failed() == 0 && !imports.outOfTime();
    }
  }
}
