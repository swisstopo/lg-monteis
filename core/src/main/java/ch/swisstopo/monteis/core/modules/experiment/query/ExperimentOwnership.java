package ch.swisstopo.monteis.core.modules.experiment.query;

import java.util.Set;
import java.util.UUID;

/** The stored owner ids of an experiment, whether or not they are still PIs. */
public record ExperimentOwnership(UUID experimentId, Set<UUID> ownerIds) {

  public ExperimentOwnership {
    ownerIds = Set.copyOf(ownerIds);
  }

  public boolean hasOwners() {
    return !ownerIds.isEmpty();
  }

  public boolean isOwner(UUID userId) {
    return ownerIds.contains(userId);
  }
}
