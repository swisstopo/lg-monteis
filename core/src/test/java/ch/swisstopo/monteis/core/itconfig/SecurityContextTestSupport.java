package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Binds an {@link org.springframework.security.core.Authentication} for the duration of a test
 * action, so integration tests that call repositories directly (bypassing the HTTP filter chain)
 * exercise the same row-level security policies a real request would.
 */
public final class SecurityContextTestSupport {

  private SecurityContextTestSupport() {}

  public static void runAsAdmin(Runnable action) {
    runAs(
        List.of(new SimpleGrantedAuthority(MonteisJwtAuthenticationConverter.READ_ALL_AUTHORITY)),
        List.of(),
        action);
  }

  public static void runAsUser(List<Long> experimentIds, Runnable action) {
    runAs(
        List.of(new SimpleGrantedAuthority(MonteisJwtAuthenticationConverter.READ_AUTHORITY)),
        experimentIds,
        action);
  }

  public static void runAs(
      List<GrantedAuthority> authorities, List<Long> experimentIds, Runnable action) {
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), "test", experimentIds);
    var authentication = new MonteisAuthenticationToken(null, principal, authorities);

    SecurityContext previous = SecurityContextHolder.getContext();
    try {
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      action.run();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }
}
