package ch.swisstopo.monteis.core.infrastructure.security;

/** The monteis-client roles Keycloak puts into {@code monteis_access.roles}. */
final class KeycloakClientRoles {
  private static final String MONTEIS_CLIENT = "monteis-client:";

  static final String EXPERIMENT_READ = MONTEIS_CLIENT + "experiment:read";
  static final String EXPERIMENT_WRITE = MONTEIS_CLIENT + "experiment:write";
  static final String EXPERIMENT_READ_ALL = MONTEIS_CLIENT + "experiment:read:all";
  static final String EXPERIMENT_WRITE_ALL = MONTEIS_CLIENT + "experiment:write:all";
  static final String DOCUMENTS_READ = MONTEIS_CLIENT + "documents:read";
  static final String ADMIN = MONTEIS_CLIENT + "admin";

  private KeycloakClientRoles() {}
}
