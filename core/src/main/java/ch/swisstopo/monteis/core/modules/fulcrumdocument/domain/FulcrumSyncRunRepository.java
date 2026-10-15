package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.util.Optional;
import java.util.UUID;

public interface FulcrumSyncRunRepository {

  // only called under the sync lock, so a run still RUNNING belongs to a pod that died and is
  // closed as FAILED first
  FulcrumSyncRun start(String triggeredBy);

  void finish(UUID runId, FulcrumSyncResult result);

  Optional<FulcrumSyncRun> findLatest();
}
