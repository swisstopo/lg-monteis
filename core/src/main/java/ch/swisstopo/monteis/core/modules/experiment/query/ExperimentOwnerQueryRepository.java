package ch.swisstopo.monteis.core.modules.experiment.query;

import java.util.List;

public interface ExperimentOwnerQueryRepository {

  List<StoredOwners> findStoredOwners();
}
