package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read access to the users of the identity provider, with the permissions of the current caller.
 * Nothing returned here is persisted. Never throws for an identity provider that fails, the
 * outcome says so.
 */
public interface UserDirectory {

  /**
   * The PIs of each experiment, possibly from a short lived cache. For anything that reads. Once
   * the identity provider is unavailable it is not asked again within the same call.
   */
  Map<UUID, Pis> pisForReading(Collection<UUID> experimentIds);

  default Pis pisForReading(UUID experimentId) {
    return pisForReading(List.of(experimentId)).get(experimentId);
  }

  /** Always asks the identity provider. For anything that writes. */
  Pis pisForWriting(UUID experimentId);
}
