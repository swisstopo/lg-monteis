package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDisabledException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLock;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.HeldSyncLock;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class FulcrumDocumentSyncRunner {

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentSyncRunner.class);

  // pods fire together, lagging clock would retake lock after a quick sync and sync twice
  private static final Duration SCHEDULED_SYNC_KEPT_AT_LEAST_FOR = Duration.ofMinutes(1);

  private final FulcrumDocumentSyncService syncService;
  private final FulcrumSyncLock syncLock;
  private final FulcrumSyncRepository syncRepository;
  private final TaskExecutor taskExecutor;
  private final FulcrumProperties fulcrumProperties;
  private final FulcrumDocumentSyncProperties syncProperties;
  private final Clock clock;

  public FulcrumDocumentSyncRunner(
      FulcrumDocumentSyncService syncService,
      FulcrumSyncLock syncLock,
      FulcrumSyncRepository syncRepository,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
      FulcrumProperties fulcrumProperties,
      FulcrumDocumentSyncProperties syncProperties,
      Clock clock) {
    this.syncService = syncService;
    this.syncLock = syncLock;
    this.syncRepository = syncRepository;
    this.taskExecutor = taskExecutor;
    this.fulcrumProperties = fulcrumProperties;
    this.syncProperties = syncProperties;
    this.clock = clock;
  }

  public FulcrumSyncStatus currentStatus() {
    return new FulcrumSyncStatus(
        syncLock.isSyncRunning(), syncRepository.findLastSuccessfulSyncAt().orElse(null));
  }

  // disabled Fulcrum would log a failed sync nightly
  public void runUnlessRunning() {
    if (!fulcrumProperties.enabled()) {
      log.info("Fulcrum is switched off, skipping the Fulcrum Document sync");
      return;
    }
    syncLock
        .tryAcquire(SCHEDULED_SYNC_KEPT_AT_LEAST_FOR)
        .ifPresentOrElse(
            this::runAndRelease,
            () -> log.info("Fulcrum Document sync already running, skipping this one"));
  }

  // lock taken here so caller learns at once, background sync releases it
  public void startInBackground() {
    if (!fulcrumProperties.enabled()) {
      throw new FulcrumDisabledException();
    }
    HeldSyncLock lock =
        syncLock.tryAcquire(Duration.ZERO).orElseThrow(FulcrumSyncAlreadyRunningException::new);
    try {
      taskExecutor.execute(() -> runAndRelease(lock));
    } catch (RuntimeException e) {
      lock.close();
      throw e;
    }
  }

  private void runAndRelease(HeldSyncLock lock) {
    try (lock) {
      SystemSecurityContext.runAsSystem(this::runQuietly);
    }
  }

  // only catch for a crashed sync, sync logs its own outcome
  private void runQuietly() {
    try {
      syncService.sync(clock.instant().plus(syncProperties.timeBudget()));
    } catch (RuntimeException e) {
      log.error("Fulcrum Document sync failed", e);
    }
  }
}
