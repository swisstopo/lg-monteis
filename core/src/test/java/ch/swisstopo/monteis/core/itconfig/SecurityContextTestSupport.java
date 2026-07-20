package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.AccessLevel;
import ch.swisstopo.monteis.core.infrastructure.security.SecurityContext;
import java.util.Set;

/**
 * Binds a {@link SecurityContext} for the duration of a test action, so integration tests that
 * call repositories directly (bypassing the HTTP filter chain) exercise the same row-level
 * security policies a real request would.
 */
public final class SecurityContextTestSupport {

  private SecurityContextTestSupport() {}

  public static void runAsAdmin(Runnable action) {
    runAs(AccessLevel.ADMIN, Set.of(), action);
  }

  public static void runAsUser(Set<Long> experimentIds, Runnable action) {
    runAs(AccessLevel.USER, experimentIds, action);
  }

  public static void runAs(AccessLevel accessLevel, Set<Long> experimentIds, Runnable action) {
    ScopedValue.where(SecurityContext.CURRENT, new SecurityContext(accessLevel, experimentIds))
        .run(action);
  }
}
