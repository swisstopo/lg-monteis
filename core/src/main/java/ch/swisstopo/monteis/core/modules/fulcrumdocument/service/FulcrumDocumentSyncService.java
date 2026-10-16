package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentTooLargeException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// mirrors the Fulcrum literature into MONTEIS (ADR "Fulcrum Documents spiegeln statt On-Demand
// abrufen"). no transaction around a sync, it would hold a db connection for minutes
@Service
public class FulcrumDocumentSyncService {

  public static final String SYNC_METRIC = "monteis.fulcrum.documents.sync";
  public static final String FAILED_FILES_METRIC = "monteis.fulcrum.documents.sync.failed.files";

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentSyncService.class);

  // every pod fires at the same time, one whose clock lags a few seconds would otherwise take the
  // lock right after a quick sync on another pod released it and sync a second time
  private static final Duration SCHEDULED_SYNC_KEPT_AT_LEAST_FOR = Duration.ofMinutes(1);

  private final FulcrumService fulcrumService;
  private final FulcrumDocumentImportService importService;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumSyncRepository syncRepository;
  private final FulcrumLiteratureMapper literatureMapper;
  private final FulcrumSyncLock syncLock;
  private final TaskExecutor taskExecutor;
  private final FulcrumProperties fulcrumProperties;
  private final FulcrumDocumentSyncProperties syncProperties;
  private final Clock clock;
  private final Counter successfulSyncs;
  private final Counter failedSyncs;
  private final Counter failedFiles;

  public FulcrumDocumentSyncService(
      FulcrumService fulcrumService,
      FulcrumDocumentImportService importService,
      FulcrumDocumentRepository documentRepository,
      FulcrumSyncRepository syncRepository,
      FulcrumLiteratureMapper literatureMapper,
      FulcrumSyncLock syncLock,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
      FulcrumProperties fulcrumProperties,
      FulcrumDocumentSyncProperties syncProperties,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.fulcrumService = fulcrumService;
    this.importService = importService;
    this.documentRepository = documentRepository;
    this.syncRepository = syncRepository;
    this.literatureMapper = literatureMapper;
    this.syncLock = syncLock;
    this.taskExecutor = taskExecutor;
    this.fulcrumProperties = fulcrumProperties;
    this.syncProperties = syncProperties;
    this.clock = clock;
    this.successfulSyncs = syncCounter(meterRegistry, "success");
    this.failedSyncs = syncCounter(meterRegistry, "failed");
    this.failedFiles =
        Counter.builder(FAILED_FILES_METRIC)
            .description("Fulcrum Documents whose file or metadata could not be saved")
            .register(meterRegistry);
  }

  public FulcrumSyncStatus currentStatus() {
    return new FulcrumSyncStatus(
        syncLock.isSyncRunning(), syncRepository.findLastSuccessfulSyncAt().orElse(null));
  }

  // a switched off Fulcrum would log a failed sync each night
  @Scheduled(cron = "${monteis.fulcrum.documents.sync-cron}", zone = "Europe/Zurich")
  public void syncUnlessRunning() {
    if (!fulcrumProperties.enabled()) {
      log.info("Fulcrum is switched off, skipping the Fulcrum Document sync");
      return;
    }
    syncLock
        .tryAcquire(SCHEDULED_SYNC_KEPT_AT_LEAST_FOR)
        .ifPresentOrElse(
            this::syncAndRelease,
            () -> log.info("Fulcrum Document sync already running, skipping this one"));
  }

  // the lock is taken right here so the caller learns at once that a sync is running, the sync
  // itself continues in the background and releases it
  public void startSyncInBackground() {
    fulcrumProperties.requireEnabled();
    SimpleLock lock =
        syncLock.tryAcquire(Duration.ZERO).orElseThrow(FulcrumSyncAlreadyRunningException::new);
    try {
      taskExecutor.execute(() -> syncAndRelease(lock));
    } catch (RuntimeException e) {
      lock.unlock();
      throw e;
    }
  }

  private void syncAndRelease(SimpleLock lock) {
    try {
      SystemSecurityContext.runAsSystem(this::syncQuietly);
    } finally {
      lock.unlock();
    }
  }

  // the only place a failed sync ends up, besides the log the metrics tell operations about it
  private void syncQuietly() {
    Instant start = clock.instant();
    try {
      SyncOutcome outcome = sync(start);
      failedFiles.increment(outcome.failedSaves() + outcome.imports().failed());
      if (outcome.isSuccessful()) {
        syncRepository.saveSuccessfulSync(start.atOffset(ZoneOffset.UTC));
        successfulSyncs.increment();
      } else {
        failedSyncs.increment();
      }
    } catch (RuntimeException e) {
      failedSyncs.increment();
      log.error("Fulcrum Document sync failed", e);
    }
  }

  private SyncOutcome sync(Instant start) {
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
    ImportCounts imports =
        importNewFiles(
            filesToImport(fromFulcrum, storedById), start.plus(syncProperties.timeBudget()));

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
  private ImportCounts importNewFiles(List<FileToImport> filesToImport, Instant deadline) {
    int imported = 0;
    int skipped = 0;
    int failed = 0;
    for (FileToImport toImport : filesToImport) {
      if (clock.instant().isAfter(deadline)) {
        log.info("Fulcrum Document sync ran out of time, the next one continues");
        return new ImportCounts(imported, skipped, failed, true);
      }
      try {
        importService.importFile(toImport);
        imported++;
      } catch (FulcrumAttachmentTooLargeException e) {
        skipped++;
        log.warn(
            "Skipping the file of Fulcrum Document {}: {}",
            toImport.row().recordId(),
            e.getMessage());
      } catch (RuntimeException e) {
        failed++;
        log.warn(
            "Could not import file {} of Fulcrum Document {}",
            toImport.attachment().id(),
            toImport.row().recordId(),
            e);
      }
    }
    return new ImportCounts(imported, skipped, failed, false);
  }

  private static List<FileToImport> filesToImport(
      List<FulcrumLiteratureRow> fromFulcrum, Map<UUID, FulcrumDocument> storedById) {
    return fromFulcrum.stream()
        .flatMap(
            row ->
                firstAttachmentOf(row)
                    .filter(attachment -> !isStored(attachment, storedById.get(row.recordId())))
                    .map(attachment -> new FileToImport(row, attachment))
                    .stream())
        .toList();
  }

  // every document should have exactly one file (PO), more are a mistake in Fulcrum to fix there
  private static Optional<FulcrumAttachmentReference> firstAttachmentOf(FulcrumLiteratureRow row) {
    List<FulcrumAttachmentReference> attachments = row.attachments();
    if (attachments.size() > 1) {
      log.warn(
          "Fulcrum Document {} has {} files {}, only the first one is taken",
          row.recordId(),
          attachments.size(),
          attachments.stream().map(FulcrumAttachmentReference::id).toList());
    }
    return attachments.stream().findFirst();
  }

  private static boolean isStored(
      FulcrumAttachmentReference attachment, @Nullable FulcrumDocument stored) {
    return stored != null && stored.file().attachmentId().equals(attachment.id());
  }

  private static Counter syncCounter(MeterRegistry meterRegistry, String outcome) {
    return Counter.builder(SYNC_METRIC)
        .description("Finished Fulcrum Document syncs by outcome")
        .tag("outcome", outcome)
        .register(meterRegistry);
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
