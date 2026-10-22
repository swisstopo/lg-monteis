package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FulcrumSyncRepository {

  // ShedLock can only take a lock, not tell if held
  boolean isSyncRunning();

  Optional<OffsetDateTime> findLastSuccessfulSyncAt();

  void saveSuccessfulSync(OffsetDateTime startedAt);
}
