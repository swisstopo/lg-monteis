package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachments;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumAttachment;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncResult;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRun;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRunRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// mirrors the Fulcrum literature into MONTEIS (ADR "Fulcrum Documents spiegeln statt On-Demand
// abrufen"). no transaction around a run, it would hold a db connection for minutes. every run,
// scheduled or manual, holds the ShedLock lock from start to finish, so only one pod syncs at a
// time
@Service
public class FulcrumDocumentSyncService {

  public static final String LOCK_NAME = "fulcrum-document-sync";

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentSyncService.class);

  private final FulcrumService fulcrumService;
  private final FulcrumAttachments fulcrumAttachments;
  private final FulcrumEntryRepository entryRepository;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumSyncRunRepository runRepository;
  private final FulcrumDocumentStorage storage;
  private final LockProvider lockProvider;
  private final TaskExecutor taskExecutor;
  private final FulcrumProperties fulcrumProperties;
  private final FulcrumDocumentSyncProperties syncProperties;
  private final Clock clock;

  public FulcrumDocumentSyncService(
      FulcrumService fulcrumService,
      FulcrumAttachments fulcrumAttachments,
      FulcrumEntryRepository entryRepository,
      FulcrumDocumentRepository documentRepository,
      FulcrumSyncRunRepository runRepository,
      FulcrumDocumentStorage storage,
      LockProvider lockProvider,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
      FulcrumProperties fulcrumProperties,
      FulcrumDocumentSyncProperties syncProperties,
      Clock clock) {
    this.fulcrumService = fulcrumService;
    this.fulcrumAttachments = fulcrumAttachments;
    this.entryRepository = entryRepository;
    this.documentRepository = documentRepository;
    this.runRepository = runRepository;
    this.storage = storage;
    this.lockProvider = lockProvider;
    this.taskExecutor = taskExecutor;
    this.fulcrumProperties = fulcrumProperties;
    this.syncProperties = syncProperties;
    this.clock = clock;
  }

  public Optional<FulcrumSyncRun> findLatestRun() {
    return runRepository.findLatest();
  }

  // without a token every environment that has none (local, ci) would log a failed run each night
  @Scheduled(cron = "${monteis.fulcrum.documents.sync-cron}", zone = "Europe/Zurich")
  public void syncOnSchedule() {
    if (fulcrumProperties.apiToken() == null || fulcrumProperties.apiToken().isBlank()) {
      log.info("No Fulcrum API token configured, skipping the scheduled Fulcrum Document sync");
      return;
    }
    acquireLock()
        .ifPresentOrElse(
            lock -> syncAsSystem(lock, startRun(lock, FulcrumSyncRun.TRIGGERED_BY_SCHEDULE)),
            () -> log.info("Fulcrum Document sync already running, skipping the scheduled one"));
  }

  // the lock is taken here so the caller learns right away that a sync is running, the run itself
  // continues in the background and releases it
  public FulcrumSyncRun startSync(String triggeredBy) {
    SimpleLock lock = acquireLock().orElseThrow(FulcrumSyncAlreadyRunningException::new);
    FulcrumSyncRun run = startRun(lock, triggeredBy);
    try {
      taskExecutor.execute(() -> syncAsSystem(lock, run));
    } catch (RuntimeException e) {
      lock.unlock();
      throw e;
    }
    return run;
  }

  private Optional<SimpleLock> acquireLock() {
    return lockProvider.lock(
        new LockConfiguration(
            clock.instant(), LOCK_NAME, syncProperties.lockAtMostFor(), Duration.ZERO));
  }

  private FulcrumSyncRun startRun(SimpleLock lock, String triggeredBy) {
    try {
      return runRepository.start(triggeredBy);
    } catch (RuntimeException e) {
      lock.unlock();
      throw e;
    }
  }

  private void syncAsSystem(SimpleLock lock, FulcrumSyncRun run) {
    try {
      SystemSecurityContext.runAsSystem(() -> runRepository.finish(run.id(), syncQuietly(run)));
    } finally {
      lock.unlock();
    }
  }

  private FulcrumSyncResult syncQuietly(FulcrumSyncRun run) {
    try {
      return sync(run);
    } catch (RuntimeException e) {
      log.error("Fulcrum Document sync {} failed", run.id(), e);
      return FulcrumSyncResult.failed();
    }
  }

  private FulcrumSyncResult sync(FulcrumSyncRun run) {
    Instant deadline = clock.instant().plus(syncProperties.timeBudget());
    List<FulcrumEntry> entries =
        fulcrumService.getLiteratureToSync().stream()
            .map(FulcrumDocumentSyncService::toEntry)
            .toList();
    entryRepository.saveSeen(entries, run.startedAt());
    int removed = entryRepository.markRemovedUnlessSeenAt(run.startedAt());
    documentRepository.reconcileWithEntries();
    log.info(
        "Fulcrum Document sync {}: {} entries seen, {} newly removed in Fulcrum",
        run.id(),
        entries.size(),
        removed);

    int stored = 0;
    int failed = 0;
    for (FulcrumAttachment attachment : entryRepository.findAttachmentsToStore()) {
      if (clock.instant().isAfter(deadline)) {
        log.info("Fulcrum Document sync {} ran out of time, the next run continues", run.id());
        return new FulcrumSyncResult(FulcrumSyncStatus.INCOMPLETE, entries.size(), stored, failed);
      }
      if (storeQuietly(attachment)) {
        stored++;
      } else {
        failed++;
      }
    }
    FulcrumSyncStatus status =
        failed == 0 ? FulcrumSyncStatus.SUCCEEDED : FulcrumSyncStatus.INCOMPLETE;
    return new FulcrumSyncResult(status, entries.size(), stored, failed);
  }

  // storage first and the row after, like an experiment document upload: a failure in between
  // leaves an object nobody sees, the next run overwrites it under the same key. one failed
  // attachment must not stop the others
  private boolean storeQuietly(FulcrumAttachment attachment) {
    Path download = null;
    try {
      download = Files.createTempFile("fulcrum-document-", ".download");
      try (InputStream content = fulcrumAttachments.open(attachment.id())) {
        Files.copy(content, download, StandardCopyOption.REPLACE_EXISTING);
      }
      DocumentMetadata metadata =
          DocumentMetadata.of(
              fileNameOf(attachment.fileName()),
              contentTypeOf(attachment.fileName()),
              Files.size(download));
      storage.store(attachment.id(), metadata.contentType(), download);
      documentRepository.create(attachment.id(), attachment.entryId(), metadata);
      return true;
    } catch (IOException | RuntimeException e) {
      log.warn(
          "Could not store attachment {} of Fulcrum entry {}",
          attachment.id(),
          attachment.entryId(),
          e);
      return false;
    } finally {
      deleteQuietly(download);
    }
  }

  private static void deleteQuietly(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not delete the temporary download {}", file, e);
    }
  }

  private static FulcrumEntry toEntry(FulcrumLiteratureEntry entry) {
    return new FulcrumEntry(
        entry.recordId(),
        entry.number(),
        entry.title(),
        entry.authors(),
        entry.year(),
        entry.category(),
        entry.keywords(),
        entry.attachments().stream()
            .map(
                attachment ->
                    new FulcrumAttachment(attachment.id(), entry.recordId(), attachment.name()))
            .toList(),
        null);
  }

  private static String fileNameOf(String name) {
    return name.length() <= DocumentMetadata.MAX_LENGTH
        ? name
        : name.substring(0, DocumentMetadata.MAX_LENGTH);
  }

  // Fulcrum serves every file as application/octet-stream, the browser could not show a pdf
  private static String contentTypeOf(String fileName) {
    return MediaTypeFactory.getMediaType(fileName)
        .map(MediaType::toString)
        .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }
}
