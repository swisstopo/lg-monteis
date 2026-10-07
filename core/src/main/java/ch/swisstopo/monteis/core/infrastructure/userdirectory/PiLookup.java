package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.List;

/**
 * What the user directory knows about the PIs of an experiment. Only {@link Found} is a complete
 * answer, every other outcome means the PIs are unknown right now and nothing may be concluded
 * from their absence.
 */
public sealed interface PiLookup {

  /** The experiment's write group exists, these are its enabled members. */
  record Found(List<DirectoryUser> pis) implements PiLookup {
    public Found {
      pis = List.copyOf(pis);
    }
  }

  /** No group grants write access to the experiment, or the caller cannot see it. */
  record NoWriteGroup() implements PiLookup {}

  /** Keycloak answered 403, the realm's permissions do not match what core expects. */
  record Denied(String reason) implements PiLookup {}

  /** Keycloak could not be asked. */
  record Unavailable(String reason) implements PiLookup {}
}
