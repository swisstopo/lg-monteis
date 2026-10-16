package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FulcrumSyncRepository {

  // ShedLock can only take a lock, not tell whether someone else holds it
  boolean isSyncRunning();

  Optional<OffsetDateTime> findLastSuccessfulSyncAt();

  void saveSuccessfulSync(OffsetDateTime startedAt);
}
