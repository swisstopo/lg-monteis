package ch.swisstopo.monteis.core.infrastructure.security;

/** The JWT claim names {@link KeycloakClaimExtractor} reads. */
final class KeycloakClaims {

  static final String USERNAME = "preferred_username";
  static final String CLIENT_ACCESS = "monteis_access";
  static final String CLIENT_ACCESS_ROLES = "roles";
  static final String READ_EXPERIMENTS = "read_experiment_ids";
  static final String WRITE_EXPERIMENTS = "write_experiment_ids";

  private KeycloakClaims() {}
}
