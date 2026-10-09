package ch.swisstopo.monteis.core.modules.experiment.query;

import java.util.Set;
import java.util.UUID;

public record StoredOwners(UUID experimentId, Set<UUID> ownerIds) {

  public StoredOwners {
    ownerIds = Set.copyOf(ownerIds);
  }

  public boolean hasOwners() {
    return !ownerIds.isEmpty();
  }

  public boolean isOwner(UUID userId) {
    return ownerIds.contains(userId);
  }
}
