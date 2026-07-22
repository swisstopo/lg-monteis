package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Collection;
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
  public Object getPrincipal() {
    return principal;
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
