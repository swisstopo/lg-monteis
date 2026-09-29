package ch.swisstopo.monteis.core.infrastructure.security;

/**
 * The {@code api:*} authorities {@link MonteisJwtAuthenticationConverter} grants, one per {@link
 * KeycloakClientRoles} role. Only {@link AccessPolicy} turns them into decisions.
 */
public final class MonteisAuthorities {

  // Read on the experiments in MonteisPrincipal#readExperimentIds only.
  public static final String EXPERIMENT_READ_AUTHORITY = "api:experiment:read";
  // Edit on the experiments in MonteisPrincipal#writeExperimentIds only.
  public static final String EXPERIMENT_WRITE_AUTHORITY = "api:experiment:write";
  // Read and edit on every experiment (global editor).
  public static final String EXPERIMENT_WRITE_ALL_AUTHORITY = "api:experiment:write-all";
  public static final String DOCUMENTS_READ_AUTHORITY = "api:documents:read";
  // Every experiment plus the admin-only functions: create experiment, sensor catalogue, and
  // every other write (BR4.5, BR4.8).
  public static final String ADMIN_AUTHORITY = "api:admin";

  private MonteisAuthorities() {}
}
