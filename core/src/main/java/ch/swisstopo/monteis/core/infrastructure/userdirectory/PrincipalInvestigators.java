package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.util.List;

/**
 * The PIs of an experiment as far as Keycloak could tell. Only {@link Known} is a complete answer,
 * for every other case the PIs are unknown right now and nothing may be concluded from their
 * absence.
 */
public sealed interface PrincipalInvestigators {

  /** The experiment's write group exists, these are its enabled members. */
  record Known(List<DirectoryUser> users) implements PrincipalInvestigators {
    public Known {
      users = List.copyOf(users);
    }
  }

  /** No group grants write access to the experiment, or the caller cannot see it. */
  record NoWriteGroup() implements PrincipalInvestigators {}

  /** Keycloak answered 403, the realm's permissions do not match what core expects. */
  record AccessDenied(String reason) implements PrincipalInvestigators {}

  /** Keycloak could not be asked. */
  record KeycloakUnavailable(String reason) implements PrincipalInvestigators {}
}
