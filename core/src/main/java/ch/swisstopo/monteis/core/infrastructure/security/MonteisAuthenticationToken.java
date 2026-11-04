package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The one {@code Authentication} type this app binds: for real requests ({@link
 * MonteisJwtAuthenticationConverter}, {@code jwt} set) and the system pseudo-user ({@link
 * SystemSecurityContext}, {@code jwt} {@code null}).
 */
public class MonteisAuthenticationToken extends AbstractAuthenticationToken {

  private final @Nullable Jwt jwt;
  private final transient MonteisPrincipal principal;
  private final ExperimentAccess experimentAccess;

  public MonteisAuthenticationToken(
      @Nullable Jwt jwt,
      MonteisPrincipal principal,
      @Nullable Collection<? extends GrantedAuthority> authorities,
      Collection<UUID> readExperimentIds,
      Collection<UUID> writeExperimentIds) {
    super(authorities);
    this.jwt = jwt;
    this.principal = principal;
    this.experimentAccess =
        ExperimentAccess.of(permissionsOf(getAuthorities()), readExperimentIds, writeExperimentIds);
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

  public ExperimentAccess experimentAccess() {
    return experimentAccess;
  }

  private static Set<String> permissionsOf(Collection<GrantedAuthority> authorities) {
    return authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof MonteisAuthenticationToken other
        && getClass() == other.getClass()
        && super.equals(other)
        && experimentAccess.equals(other.experimentAccess);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
