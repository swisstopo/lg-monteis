package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Transitional delegates to {@link AccessPolicy}, kept only so that {@code RlsConnectionProvider}
 * (owned by U3 u3-db-rls) compiles unchanged while its values already follow the new rules.
 * Deprecated for new code: ask {@link AccessPolicy} instead. U3 switches {@code
 * RlsConnectionProvider} to {@link AccessPolicy} and removes this class.
 *
 * <p>Not annotated {@code @Deprecated} on purpose: {@code CodingStandardsTest} forbids using
 * deprecated APIs, and {@code RlsConnectionProvider} still calls these methods until U3.
 */
public final class AuthorityChecks {

  private AuthorityChecks() {}

  public static boolean hasAuthority(Authentication authentication, String authority) {
    return hasAuthority(authentication.getAuthorities(), authority);
  }

  public static boolean hasAuthority(
      Collection<? extends GrantedAuthority> authorities, String authority) {
    return authorities.stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  /** Whether the token's read experiment ids are trusted: {@code api:experiment:read}. */
  public static boolean canReadAnyExperiment(Collection<? extends GrantedAuthority> authorities) {
    return AccessPolicy.grantsScopedExperimentRead(authorities);
  }

  public static boolean canReadAllExperiments(Authentication authentication) {
    return AccessPolicy.capabilitiesOf(authentication).canReadAllExperiments();
  }

  public static boolean canWriteAllExperiments(Authentication authentication) {
    return AccessPolicy.capabilitiesOf(authentication).canWriteAllExperiments();
  }
}
