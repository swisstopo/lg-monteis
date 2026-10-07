package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.UUID;

/**
 * Read access to the users of the identity provider, with the permissions of the current caller.
 * Nothing returned here is persisted. Never throws for an identity provider that fails, the
 * outcome says so.
 */
public interface UserDirectory {

  /** The PIs of the experiment, possibly from a short lived cache. For anything that reads. */
  Pis pisForReading(UUID experimentId);

  /** Like {@link #pisForReading} but always asks the identity provider. For anything that writes. */
  Pis pisForWriting(UUID experimentId);
}
