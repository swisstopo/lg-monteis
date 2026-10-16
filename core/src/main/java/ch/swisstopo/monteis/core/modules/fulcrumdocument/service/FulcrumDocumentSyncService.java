package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachments;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryDetails;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLockRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
// time and the lock tells whether a sync is running
@Service
public class FulcrumDocumentSyncService {

  public static final String LOCK_NAME = "fulcrum-document-sync";

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentSyncService.class);

  private final FulcrumService fulcrumService;
  private final FulcrumAttachments fulcrumAttachments;
  private final FulcrumEntryRepository entryRepository;
  private final FulcrumSyncLockRepository lockRepository;
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
      FulcrumSyncLockRepository lockRepository,
      FulcrumDocumentStorage storage,
      LockProvider lockProvider,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
      FulcrumProperties fulcrumProperties,
      FulcrumDocumentSyncProperties syncProperties,
      Clock clock) {
    this.fulcrumService = fulcrumService;
    this.fulcrumAttachments = fulcrumAttachments;
    this.entryRepository = entryRepository;
    this.lockRepository = lockRepository;
    this.storage = storage;
    this.lockProvider = lockProvider;
    this.taskExecutor = taskExecutor;
    this.fulcrumProperties = fulcrumProperties;
    this.syncProperties = syncProperties;
    this.clock = clock;
  }

  public FulcrumSyncStatus getStatus() {
    return new FulcrumSyncStatus(
        lockRepository.isHeld(LOCK_NAME), entryRepository.findLastSeenAt().orElse(null));
  }

  // without a token every environment that has none (local, ci) would log a failed sync each night
  @Scheduled(cron = "${monteis.fulcrum.documents.sync-cron}", zone = "Europe/Zurich")
  public void syncOnSchedule() {
    if (fulcrumProperties.apiToken() == null || fulcrumProperties.apiToken().isBlank()) {
      log.info("No Fulcrum API token configured, skipping the scheduled Fulcrum Document sync");
      return;
    }
    acquireLock()
        .ifPresentOrElse(
            this::syncAsSystem,
            () -> log.info("Fulcrum Document sync already running, skipping the scheduled one"));
  }

  // the lock is taken here so the caller learns right away that a sync is running, the sync itself
  // continues in the background and releases it
  public FulcrumSyncStatus startSync() {
    SimpleLock lock = acquireLock().orElseThrow(FulcrumSyncAlreadyRunningException::new);
    try {
      taskExecutor.execute(() -> syncAsSystem(lock));
    } catch (RuntimeException e) {
      lock.unlock();
      throw e;
    }
    return new FulcrumSyncStatus(true, entryRepository.findLastSeenAt().orElse(null));
  }

  private Optional<SimpleLock> acquireLock() {
    return lockProvider.lock(
        new LockConfiguration(
            clock.instant(), LOCK_NAME, syncProperties.lockAtMostFor(), Duration.ZERO));
  }

  private void syncAsSystem(SimpleLock lock) {
    try {
      SystemSecurityContext.runAsSystem(this::syncQuietly);
    } finally {
      lock.unlock();
    }
  }

  private void syncQuietly() {
    try {
      sync();
    } catch (RuntimeException e) {
      log.error("Fulcrum Document sync failed", e);
    }
  }

  private void sync() {
    Instant now = clock.instant();
    OffsetDateTime seenAt = now.atOffset(ZoneOffset.UTC);
    Instant deadline = now.plus(syncProperties.timeBudget());
    List<FulcrumLiteratureEntry> fromFulcrum = fulcrumService.getLiteratureToSync();
    Map<UUID, UUID> storedDocumentIds = entryRepository.findDocumentIdsByEntry();

    entryRepository.saveSeenDetails(
        fromFulcrum.stream()
            .filter(entry -> storedDocumentIds.containsKey(entry.recordId()))
            .map(FulcrumDocumentSyncService::detailsOf)
            .toList(),
        seenAt);
    int removed = entryRepository.markRemovedUnlessSeenAt(seenAt);

    int stored = 0;
    int failed = 0;
    for (FulcrumLiteratureEntry entry : fromFulcrum) {
      Optional<FulcrumAttachmentReference> attachment = firstAttachmentOf(entry);
      if (attachment.isEmpty()
          || Objects.equals(attachment.get().id(), storedDocumentIds.get(entry.recordId()))) {
        continue;
      }
      if (clock.instant().isAfter(deadline)) {
        log.info("Fulcrum Document sync ran out of time, the next one continues");
        break;
      }
      if (storeQuietly(entry, attachment.get(), seenAt)) {
        stored++;
      } else {
        failed++;
      }
    }
    log.info(
        "Fulcrum Document sync: {} entries from Fulcrum, {} newly removed, {} documents stored,"
            + " {} failed",
        fromFulcrum.size(),
        removed,
        stored,
        failed);
  }

  // every entry should have exactly one file (PO), more are a mistake in Fulcrum to fix there
  private static Optional<FulcrumAttachmentReference> firstAttachmentOf(
      FulcrumLiteratureEntry entry) {
    List<FulcrumAttachmentReference> attachments = entry.attachments();
    if (attachments.size() > 1) {
      log.warn(
          "Fulcrum entry {} has {} files {}, only the first one is taken",
          entry.recordId(),
          attachments.size(),
          attachments.stream().map(FulcrumAttachmentReference::id).toList());
    }
    return attachments.stream().findFirst();
  }

  // storage first and the row after, like an experiment document upload: a failure in between
  // leaves an object nobody sees, the next run overwrites it under the same key. one failed entry
  // must not stop the others
  private boolean storeQuietly(
      FulcrumLiteratureEntry entry, FulcrumAttachmentReference attachment, OffsetDateTime seenAt) {
    Path download = null;
    try {
      download = Files.createTempFile("fulcrum-document-", ".download");
      try (InputStream content = fulcrumAttachments.open(attachment.id())) {
        Files.copy(content, download, StandardCopyOption.REPLACE_EXISTING);
      }
      DocumentMetadata metadata =
          DocumentMetadata.of(
              fileNameOf(attachment.name()),
              contentTypeOf(attachment.name()),
              Files.size(download));
      storage.store(attachment.id(), metadata.contentType(), download);
      entryRepository.saveWithDocument(
          detailsOf(entry), new FulcrumDocument(attachment.id(), metadata), seenAt);
      return true;
    } catch (IOException | RuntimeException e) {
      log.warn("Could not store file {} of Fulcrum entry {}", attachment.id(), entry.recordId(), e);
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

  private static FulcrumEntryDetails detailsOf(FulcrumLiteratureEntry entry) {
    return new FulcrumEntryDetails(
        entry.recordId(),
        entry.number(),
        entry.title(),
        entry.authors(),
        entry.year(),
        entry.category(),
        entry.keywords());
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
