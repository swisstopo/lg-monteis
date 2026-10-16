package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.scheduling.SchedulingConfig;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.stereotype.Component;

// every sync, scheduled or manual, holds this lock from start to finish, so only one pod syncs at a
// time and the lock tells whether a sync is running
@Component
public class FulcrumSyncLock {

  private final LockProvider lockProvider;
  private final FulcrumSyncRepository syncRepository;
  private final FulcrumDocumentSyncProperties syncProperties;
  private final Clock clock;

  public FulcrumSyncLock(
      LockProvider lockProvider,
      FulcrumSyncRepository syncRepository,
      FulcrumDocumentSyncProperties syncProperties,
      Clock clock) {
    this.lockProvider = lockProvider;
    this.syncRepository = syncRepository;
    this.syncProperties = syncProperties;
    this.clock = clock;
  }

  // keptAtLeastFor holds the lock past a quick sync, from the time it was taken
  public Optional<SimpleLock> tryAcquire(Duration keptAtLeastFor) {
    return lockProvider.lock(
        new LockConfiguration(
            clock.instant(),
            SchedulingConfig.FULCRUM_DOCUMENT_SYNC_LOCK,
            syncProperties.lockAtMostFor(),
            keptAtLeastFor));
  }

  public boolean isSyncRunning() {
    return syncRepository.isSyncRunning();
  }
}
