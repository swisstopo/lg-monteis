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
 */
public class MonteisAuthenticationToken extends AbstractAuthenticationToken {

  private final @Nullable Jwt jwt;
  private final transient MonteisPrincipal principal;
  private final Set<UUID> readExperimentIds;
  private final Set<UUID> writeExperimentIds;

  public MonteisAuthenticationToken(
      @Nullable Jwt jwt,
      MonteisPrincipal principal,
      @Nullable Collection<? extends GrantedAuthority> authorities,
      Set<UUID> readExperimentIds,
      Set<UUID> writeExperimentIds) {
    super(authorities);
    this.jwt = jwt;
    this.principal = principal;
    this.readExperimentIds = Set.copyOf(readExperimentIds);
    this.writeExperimentIds = Set.copyOf(writeExperimentIds);
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

  public Set<UUID> readExperimentIds() {
    return readExperimentIds;
  }

  public Set<UUID> writeExperimentIds() {
    return writeExperimentIds;
  }

  public boolean canReadAllExperiments() {
    return hasAuthority(MonteisJwtAuthenticationConverter.EXPERIMENT_READ_ALL);
  }

  public boolean canWriteAllExperiments() {
    return hasAuthority(MonteisJwtAuthenticationConverter.EXPERIMENT_WRITE_ALL);
  }

  private boolean hasAuthority(String authority) {
    return getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof MonteisAuthenticationToken other
        && getClass() == other.getClass()
        && super.equals(other)
        && readExperimentIds.equals(other.readExperimentIds)
        && writeExperimentIds.equals(other.writeExperimentIds);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
