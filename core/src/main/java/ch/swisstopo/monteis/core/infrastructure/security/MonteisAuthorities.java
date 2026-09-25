package ch.swisstopo.monteis.core.infrastructure.security;

/**
 * The {@code api:*} authorities {@link MonteisJwtAuthenticationConverter} grants, one per {@link
 * KeycloakClientRoles} role.
 */
public final class MonteisAuthorities {

  // Read on the experiments in MonteisPrincipal#readExperimentIds only.
  public static final String EXPERIMENT_READ_AUTHORITY = "api:experiment:read";
  public static final String EXPERIMENT_READ_ALL_AUTHORITY = "api:experiment:read-all";
  // Write on the experiments in MonteisPrincipal#writeExperimentIds only.
  public static final String EXPERIMENT_WRITE_AUTHORITY = "api:experiment:write";
  public static final String EXPERIMENT_WRITE_ALL_AUTHORITY = "api:experiment:write-all";
  public static final String DOCUMENTS_READ_AUTHORITY = "api:documents:read";
  // Gates every write in SecurityConfig: the RLS write policies are USING (true), so no experiment
  // write authority may ever stand in for it.
  public static final String ADMIN_AUTHORITY = "api:admin";

  private MonteisAuthorities() {}
}
