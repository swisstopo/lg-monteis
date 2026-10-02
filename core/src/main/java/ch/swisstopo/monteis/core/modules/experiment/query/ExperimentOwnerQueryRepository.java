package ch.swisstopo.monteis.core.modules.experiment.query;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ExperimentOwnerQueryRepository {

  /**
   * The stored owner ids of every experiment the caller may read, experiments without owners are
   * left out.
   */
  Map<UUID, Set<UUID>> findOwnerIdsByExperiment();
}
