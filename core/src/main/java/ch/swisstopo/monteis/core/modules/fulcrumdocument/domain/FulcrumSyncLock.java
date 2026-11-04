package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.Duration;
import java.util.Optional;

// held by every sync start to finish, so it also tells whether a sync runs
public interface FulcrumSyncLock {

  Optional<HeldSyncLock> tryAcquire(Duration keptAtLeastFor);

  boolean isSyncRunning();
}
