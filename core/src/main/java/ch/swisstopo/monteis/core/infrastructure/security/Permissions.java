package ch.swisstopo.monteis.core.infrastructure.security;

/**
 * The authority names {@link MonteisJwtAuthenticationConverter} maps the Keycloak client roles to.
 * {@link #WRITE_ALL} allows everything: every experiment, create experiment, sensor writes and
 * every other write.
 */
public final class Permissions {

  /** Read the experiments in {@link MonteisPrincipal#readExperimentIds()} only. */
  public static final String EXPERIMENT_READ = "api:experiment:read";

  /** Write the experiments in {@link MonteisPrincipal#writeExperimentIds()} only. */
  public static final String EXPERIMENT_WRITE = "api:experiment:write";

  public static final String WRITE_ALL = "api:write-all";

  private Permissions() {}
}
