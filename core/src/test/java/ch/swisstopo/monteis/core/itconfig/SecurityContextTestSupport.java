package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities;
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

  /** {@code api:admin}: all experiments, read and write. */
  public static void runAsAdmin(Runnable action) {
    runAs(
        List.of(new SimpleGrantedAuthority(MonteisAuthorities.ADMIN_AUTHORITY)),
        List.of(),
        List.of(),
        action);
  }

  /** {@code api:experiment:write-all}: all experiments, read and write, no admin functions. */
  public static void runAsGlobalEditor(Runnable action) {
    runAs(
        List.of(new SimpleGrantedAuthority(MonteisAuthorities.EXPERIMENT_WRITE_ALL_AUTHORITY)),
        List.of(),
        List.of(),
        action);
  }

  /** {@code api:experiment:read} on {@code readExperimentIds} only. */
  public static void runAsUser(List<UUID> readExperimentIds, Runnable action) {
    runAs(
        List.of(new SimpleGrantedAuthority(MonteisAuthorities.EXPERIMENT_READ_AUTHORITY)),
        readExperimentIds,
        List.of(),
        action);
  }

  /**
   * {@code api:experiment:read} on {@code readExperimentIds} plus {@code api:experiment:write} on
   * {@code writeExperimentIds}.
   */
  public static void runAsUser(
      List<UUID> readExperimentIds, List<UUID> writeExperimentIds, Runnable action) {
    runAs(
        List.of(
            new SimpleGrantedAuthority(MonteisAuthorities.EXPERIMENT_READ_AUTHORITY),
            new SimpleGrantedAuthority(MonteisAuthorities.EXPERIMENT_WRITE_AUTHORITY)),
        readExperimentIds,
        writeExperimentIds,
        action);
  }

  public static void runAs(
      List<GrantedAuthority> authorities,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds,
      Runnable action) {
    MonteisPrincipal principal =
        new MonteisPrincipal(UUID.randomUUID(), "test", readExperimentIds, writeExperimentIds);
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
