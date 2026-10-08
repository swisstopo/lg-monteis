package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The one {@code Authentication} type this app binds: for real requests ({@link
 * MonteisJwtAuthenticationConverter}, {@code jwt} set) and the system pseudo-user ({@link
 * SystemSecurityContext}, {@code jwt} {@code null}).
 *
 * <p>It is also the single place that holds the privilege rules (ADR-001): the RLS session context
 * and {@code /api/me} ask it, the filter chain checks its {@link Permissions} authorities.
 */
public class MonteisAuthenticationToken extends AbstractAuthenticationToken {

  private final @Nullable Jwt jwt;
  private final transient MonteisPrincipal principal;

  public MonteisAuthenticationToken(
      @Nullable Jwt jwt,
      MonteisPrincipal principal,
      @Nullable Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.jwt = jwt;
    this.principal = principal;
    super.setAuthenticated(true);
  }

  @Override
  public @Nullable Object getCredentials() {
    return jwt;
  }

  @Override
  public MonteisPrincipal getPrincipal() {
    return principal;
  }

  /** Admins and the system context: read and write everything. */
  public boolean canWriteAll() {
    return hasPermission(Permissions.WRITE_ALL);
  }

  /** The experiments the caller may read one by one; empty when {@link #canWriteAll()}. */
  public Set<UUID> readableExperimentIds() {
    return canWriteAll() || !hasPermission(Permissions.EXPERIMENT_READ)
        ? Set.of()
        : Set.copyOf(principal.readExperimentIds());
  }

  /**
   * The experiments whose metadata the caller may write one by one, always a subset of {@link
   * #readableExperimentIds()}; empty when {@link #canWriteAll()}.
   */
  public Set<UUID> writableExperimentIds() {
    return canWriteAll() || !hasPermission(Permissions.EXPERIMENT_WRITE)
        ? Set.of()
        : Set.copyOf(principal.writeExperimentIds());
  }

  private boolean hasPermission(String permission) {
    return getAuthorities().stream()
        .anyMatch(authority -> permission.equals(authority.getAuthority()));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    return obj != null && getClass() == obj.getClass() && super.equals(obj);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
