package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;

/**
 * This app's authorities and, at the same time, the facts {@link Capabilities} decides on: {@link
 * GrantedAuthorityExtractor} maps each Keycloak client role to one grant, which the {@code
 * Authentication} then carries as its {@link GrantedAuthority}.
 *
 * <p>{@link #SYSTEM_READ_ALL} has no authority string and no role, so no request token can ever
 * carry it (BR4.7); only {@link Capabilities#SYSTEM} holds it.
 */
public enum Grant implements GrantedAuthority {
  /** Read the experiments in {@link MonteisPrincipal#readExperimentIds()} only. */
  EXPERIMENT_READ("api:experiment:read"),
  /** Write the experiments in {@link MonteisPrincipal#writeExperimentIds()} only. */
  EXPERIMENT_WRITE("api:experiment:write"),
  /** Read and write every experiment (global editor). */
  EXPERIMENT_WRITE_ALL("api:experiment:write-all"),
  DOCUMENTS_READ("api:documents:read"),
  /**
   * Every experiment plus the admin-only functions: create experiment, sensor writes, and every
   * other write (BR4.5, BR4.8).
   */
  ADMIN("api:admin"),
  /** The system context of background jobs: read every experiment, nothing else. */
  SYSTEM_READ_ALL(null);

  private final String authority;

  Grant(String authority) {
    this.authority = authority;
  }

  /** The {@code api:*} name; {@code null} for {@link #SYSTEM_READ_ALL}. */
  @Override
  public String getAuthority() {
    return authority;
  }

  /** The grant named {@code authority}; empty for unknown names and for {@code null}. */
  public static Optional<Grant> fromAuthority(String authority) {
    return Arrays.stream(values())
        .filter(grant -> grant.authority != null && Objects.equals(grant.authority, authority))
        .findFirst();
  }
}
