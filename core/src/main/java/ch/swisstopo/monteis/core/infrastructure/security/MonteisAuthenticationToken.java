package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private final boolean isAdmin;
  private final Set<UUID> readableExperimentIds;
  private final Set<UUID> writableExperimentIds;

  public MonteisAuthenticationToken(
      @Nullable Jwt jwt,
      MonteisPrincipal principal,
      @Nullable Collection<? extends GrantedAuthority> authorities,
      Collection<UUID> readExperimentIds,
      Collection<UUID> writeExperimentIds) {
    super(authorities);
    this.jwt = jwt;
    this.principal = principal;
    this.isAdmin = hasPermission(Permissions.ADMIN);
    this.writableExperimentIds =
        isAdmin || !hasPermission(Permissions.EXPERIMENT_WRITE)
            ? Set.of()
            : Set.copyOf(writeExperimentIds);
    this.readableExperimentIds =
        isAdmin || !hasPermission(Permissions.EXPERIMENT_READ)
            ? Set.of()
            : union(readExperimentIds, writableExperimentIds);
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
  public boolean isAdmin() {
    return isAdmin;
  }

  /** The experiments the caller may read one by one; empty when {@link #isAdmin()}. */
  public Set<UUID> readableExperimentIds() {
    return readableExperimentIds;
  }

  /**
   * The experiments whose metadata the caller may write one by one, always a subset of {@link
   * #readableExperimentIds()}; empty when {@link #isAdmin()}.
   */
  public Set<UUID> writableExperimentIds() {
    return writableExperimentIds;
  }

  private boolean hasPermission(String permission) {
    return getAuthorities().stream()
        .anyMatch(authority -> permission.equals(authority.getAuthority()));
  }

  private static Set<UUID> union(Collection<UUID> first, Collection<UUID> second) {
    return Stream.concat(first.stream(), second.stream()).collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof MonteisAuthenticationToken other
        && getClass() == other.getClass()
        && super.equals(other)
        && readableExperimentIds.equals(other.readableExperimentIds)
        && writableExperimentIds.equals(other.writableExperimentIds);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
