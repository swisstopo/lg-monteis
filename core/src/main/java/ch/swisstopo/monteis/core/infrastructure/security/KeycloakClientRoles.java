package ch.swisstopo.monteis.core.infrastructure.security;

/**
 * The five monteis-client roles Keycloak puts into {@code monteis_access.roles} (contract C1). Any
 * other role, including the removed legacy ones, is ignored.
 */
final class KeycloakClientRoles {
  private static final String MONTEIS_CLIENT = "monteis-client:";

  static final String EXPERIMENT_READ = MONTEIS_CLIENT + "experiment:read";
  static final String EXPERIMENT_WRITE = MONTEIS_CLIENT + "experiment:write";
  static final String EXPERIMENT_WRITE_ALL = MONTEIS_CLIENT + "experiment:write:all";
  static final String DOCUMENTS_READ = MONTEIS_CLIENT + "documents:read";
  static final String ADMIN = MONTEIS_CLIENT + "admin";

  private KeycloakClientRoles() {}
}
