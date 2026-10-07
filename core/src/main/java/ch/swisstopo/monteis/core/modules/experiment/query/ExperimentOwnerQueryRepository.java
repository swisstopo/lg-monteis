package ch.swisstopo.monteis.core.modules.experiment.query;

import java.util.List;

public interface ExperimentOwnerQueryRepository {

  /** The stored owners of every experiment the caller may read, those without owners left out. */
  List<StoredOwners> findStoredOwners();
}
