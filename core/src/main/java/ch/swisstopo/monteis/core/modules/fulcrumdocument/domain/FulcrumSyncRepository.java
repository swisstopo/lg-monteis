package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FulcrumSyncRepository {

  Optional<OffsetDateTime> findLastSuccessfulSyncAt();

  void saveSuccessfulSync(OffsetDateTime startedAt);
}
