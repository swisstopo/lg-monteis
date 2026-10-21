package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDisabledException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.infrastructure.security.SystemSecurityContext;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.time.Clock;
import java.time.Duration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

// runs the sync under the conditions its callers need: Fulcrum switched on, one pod at a time
// (ShedLock), as the system user (no request user on a scheduled or background thread)
@Service
public class FulcrumDocumentSyncRunner {

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentSyncRunner.class);

  // every pod fires at the same time, one whose clock lags a few seconds would otherwise take the
  // lock right after a quick sync on another pod released it and sync a second time
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

  // a switched off Fulcrum would log a failed sync each night
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

  // the lock is taken right here so the caller learns at once that a sync is running, the sync
  // itself continues in the background and releases it
  public void startInBackground() {
    if (!fulcrumProperties.enabled()) {
      throw new FulcrumDisabledException();
    }
    SimpleLock lock =
        syncLock.tryAcquire(Duration.ZERO).orElseThrow(FulcrumSyncAlreadyRunningException::new);
    try {
      taskExecutor.execute(() -> runAndRelease(lock));
    } catch (RuntimeException e) {
      lock.unlock();
      throw e;
    }
  }

  private void runAndRelease(SimpleLock lock) {
    try {
      SystemSecurityContext.runAsSystem(this::runQuietly);
    } finally {
      lock.unlock();
    }
  }

  // the only place a crashed sync ends up, the sync itself logs how it went
  private void runQuietly() {
    try {
      syncService.sync(clock.instant().plus(syncProperties.timeBudget()));
    } catch (RuntimeException e) {
      log.error("Fulcrum Document sync failed", e);
    }
  }
}
