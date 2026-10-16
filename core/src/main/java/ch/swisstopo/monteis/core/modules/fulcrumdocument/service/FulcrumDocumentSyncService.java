package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLockRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// mirrors the Fulcrum literature into MONTEIS (ADR "Fulcrum Documents spiegeln statt On-Demand
// abrufen"). no transaction around a sync, it would hold a db connection for minutes. every sync,
// scheduled or manual, holds the ShedLock lock from start to finish, so only one pod syncs at a
// time and the lock tells whether a sync is running
@Service
public class FulcrumDocumentSyncService {

  public static final String LOCK_NAME = "fulcrum-document-sync";

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentSyncService.class);

  private final FulcrumService fulcrumService;
  private final FulcrumDocumentCopier documentCopier;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumLiteratureMapper literatureMapper;
  private final FulcrumSyncLockRepository lockRepository;
  private final LockProvider lockProvider;
  private final TaskExecutor taskExecutor;
  private final FulcrumProperties fulcrumProperties;
  private final FulcrumDocumentSyncProperties syncProperties;
  private final Clock clock;

  public FulcrumDocumentSyncService(
      FulcrumService fulcrumService,
      FulcrumDocumentCopier documentCopier,
      FulcrumDocumentRepository documentRepository,
      FulcrumLiteratureMapper literatureMapper,
      FulcrumSyncLockRepository lockRepository,
      LockProvider lockProvider,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
      FulcrumProperties fulcrumProperties,
      FulcrumDocumentSyncProperties syncProperties,
      Clock clock) {
    this.fulcrumService = fulcrumService;
    this.documentCopier = documentCopier;
    this.documentRepository = documentRepository;
    this.literatureMapper = literatureMapper;
    this.lockRepository = lockRepository;
    this.lockProvider = lockProvider;
    this.taskExecutor = taskExecutor;
    this.fulcrumProperties = fulcrumProperties;
    this.syncProperties = syncProperties;
    this.clock = clock;
  }

  public FulcrumSyncStatus currentStatus() {
    return FulcrumSyncStatus.of(
        lockRepository.isHeld(LOCK_NAME), documentRepository.findLastSeenAt());
  }

  // a switched off Fulcrum would log a failed sync each night
  @Scheduled(cron = "${monteis.fulcrum.documents.sync-cron}", zone = "Europe/Zurich")
  public void syncUnlessRunning() {
    if (!fulcrumProperties.enabled()) {
      log.info("Fulcrum is switched off, skipping the Fulcrum Document sync");
      return;
    }
    acquireLock()
        .ifPresentOrElse(
            this::syncAndRelease,
            () -> log.info("Fulcrum Document sync already running, skipping this one"));
  }

  // the lock is taken right here so the caller learns at once that a sync is running, the sync
  // itself continues in the background and releases it
  public FulcrumSyncStatus startSyncInBackground() {
    fulcrumProperties.requireEnabled();
    SimpleLock lock = acquireLock().orElseThrow(FulcrumSyncAlreadyRunningException::new);
    try {
      taskExecutor.execute(() -> syncAndRelease(lock));
    } catch (RuntimeException e) {
      lock.unlock();
      throw e;
    }
    return FulcrumSyncStatus.of(true, documentRepository.findLastSeenAt());
  }

  private Optional<SimpleLock> acquireLock() {
    return lockProvider.lock(
        new LockConfiguration(
            clock.instant(), LOCK_NAME, syncProperties.lockAtMostFor(), Duration.ZERO));
  }

  private void syncAndRelease(SimpleLock lock) {
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
    Instant start = clock.instant();
    OffsetDateTime seenAt = start.atOffset(ZoneOffset.UTC);
    List<FulcrumLiteratureEntry> fromFulcrum = fulcrumService.getLiteratureToSync();
    Map<UUID, FulcrumDocument> storedById = storedDocumentsById();

    refreshStoredDocuments(fromFulcrum, storedById, seenAt);
    int removed = documentRepository.markRemovedUnlessSeenAt(seenAt);
    CopyCounts copied =
        copyNewFiles(
            filesToCopy(fromFulcrum, storedById), seenAt, start.plus(syncProperties.timeBudget()));

    log.info(
        "Fulcrum Document sync: {} documents from Fulcrum, {} newly removed, {} files copied,"
            + " {} failed",
        fromFulcrum.size(),
        removed,
        copied.copied(),
        copied.failed());
  }

  private Map<UUID, FulcrumDocument> storedDocumentsById() {
    return documentRepository.findAll().stream()
        .collect(Collectors.toMap(FulcrumDocument::id, Function.identity()));
  }

  // a replaced file counts as seen too, the document keeps its old file until the new one is in
  private void refreshStoredDocuments(
      List<FulcrumLiteratureEntry> fromFulcrum,
      Map<UUID, FulcrumDocument> storedById,
      OffsetDateTime seenAt) {
    documentRepository.saveSeen(
        fromFulcrum.stream()
            .filter(entry -> storedById.containsKey(entry.recordId()))
            .map(
                entry ->
                    literatureMapper.toDocument(entry, storedById.get(entry.recordId()).file()))
            .toList(),
        seenAt);
  }

  private CopyCounts copyNewFiles(
      List<FileToCopy> filesToCopy, OffsetDateTime seenAt, Instant deadline) {
    int copied = 0;
    int failed = 0;
    for (FileToCopy toCopy : filesToCopy) {
      if (clock.instant().isAfter(deadline)) {
        log.info("Fulcrum Document sync ran out of time, the next one continues");
        break;
      }
      if (documentCopier.copyQuietly(toCopy.entry(), toCopy.attachment(), seenAt)) {
        copied++;
      } else {
        failed++;
      }
    }
    return new CopyCounts(copied, failed);
  }

  // new documents, and stored ones whose file Fulcrum replaced
  private static List<FileToCopy> filesToCopy(
      List<FulcrumLiteratureEntry> fromFulcrum, Map<UUID, FulcrumDocument> storedById) {
    return fromFulcrum.stream()
        .flatMap(
            entry ->
                firstAttachmentOf(entry)
                    .filter(attachment -> !isStored(attachment, storedById.get(entry.recordId())))
                    .map(attachment -> new FileToCopy(entry, attachment))
                    .stream())
        .toList();
  }

  private static boolean isStored(
      FulcrumAttachmentReference attachment, @Nullable FulcrumDocument stored) {
    return stored != null && stored.file().attachmentId().equals(attachment.id());
  }

  // every document should have exactly one file (PO), more are a mistake in Fulcrum to fix there
  private static Optional<FulcrumAttachmentReference> firstAttachmentOf(
      FulcrumLiteratureEntry entry) {
    List<FulcrumAttachmentReference> attachments = entry.attachments();
    if (attachments.size() > 1) {
      log.warn(
          "Fulcrum Document {} has {} files {}, only the first one is taken",
          entry.recordId(),
          attachments.size(),
          attachments.stream().map(FulcrumAttachmentReference::id).toList());
    }
    return attachments.stream().findFirst();
  }

  private record FileToCopy(FulcrumLiteratureEntry entry, FulcrumAttachmentReference attachment) {}

  private record CopyCounts(int copied, int failed) {}
}
