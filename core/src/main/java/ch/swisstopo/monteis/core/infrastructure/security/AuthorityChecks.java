package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.ADMIN_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_ALL_AUTHORITY;

import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** The authority questions the filter chain, the RLS context and {@code /api/me} all ask. */
public final class AuthorityChecks {

  private AuthorityChecks() {}

  public static boolean hasAuthority(Authentication authentication, String authority) {
    return hasAuthority(authentication.getAuthorities(), authority);
  }

  public static boolean hasAuthority(
      Collection<? extends GrantedAuthority> authorities, String authority) {
    return authorities.stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  public static boolean canReadAnyExperiment(Collection<? extends GrantedAuthority> authorities) {
    return hasAuthority(authorities, EXPERIMENT_READ_AUTHORITY)
        || hasAuthority(authorities, EXPERIMENT_READ_ALL_AUTHORITY);
  }

  public static boolean canReadAllExperiments(Authentication authentication) {
    return hasAuthority(authentication, EXPERIMENT_READ_ALL_AUTHORITY);
  }

  // admin gates every write in SecurityConfig, so it implies write on every experiment
  public static boolean canWriteAllExperiments(Authentication authentication) {
    return hasAuthority(authentication, ADMIN_AUTHORITY)
        || hasAuthority(authentication, EXPERIMENT_WRITE_ALL_AUTHORITY);
  }
}
