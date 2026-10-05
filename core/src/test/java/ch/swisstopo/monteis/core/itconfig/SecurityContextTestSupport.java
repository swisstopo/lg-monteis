package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.Grant;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Binds an {@link org.springframework.security.core.Authentication} for the duration of a test
 * action, so integration tests that call repositories directly (bypassing the HTTP filter chain)
 * exercise the same row-level security policies a real request would.
 */
public final class SecurityContextTestSupport {

  /** The username every bound principal carries. */
  public static final String USERNAME = "test";

  private SecurityContextTestSupport() {}

  /** {@code api:admin}: all experiments, read and write. */
  public static void runAsAdmin(Runnable action) {
    runAs(List.of(Grant.ADMIN), List.of(), List.of(), action);
  }

  /** {@code api:experiment:write-all}: all experiments, read and write, no admin functions. */
  public static void runAsGlobalEditor(Runnable action) {
    runAs(List.of(Grant.EXPERIMENT_WRITE_ALL), List.of(), List.of(), action);
  }

  /** {@code api:experiment:read} on {@code readExperimentIds} only. */
  public static void runAsUser(List<UUID> readExperimentIds, Runnable action) {
    runAs(List.of(Grant.EXPERIMENT_READ), readExperimentIds, List.of(), action);
  }

  /**
   * {@code api:experiment:read} on {@code readExperimentIds} plus {@code api:experiment:write} on
   * {@code writeExperimentIds}.
   */
  public static void runAsUser(
      List<UUID> readExperimentIds, List<UUID> writeExperimentIds, Runnable action) {
    runAs(
        List.of(Grant.EXPERIMENT_READ, Grant.EXPERIMENT_WRITE),
        readExperimentIds,
        writeExperimentIds,
        action);
  }

  /** {@link #runAsAdmin} for an action with a result. */
  public static <T> T callAsAdmin(Supplier<T> action) {
    return callAs(List.of(Grant.ADMIN), List.of(), List.of(), action);
  }

  public static void runAs(
      List<GrantedAuthority> authorities,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds,
      Runnable action) {
    callAs(
        authorities,
        readExperimentIds,
        writeExperimentIds,
        () -> {
          action.run();
          return null;
        });
  }

  public static <T> T callAs(
      List<GrantedAuthority> authorities,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds,
      Supplier<T> action) {
    MonteisPrincipal principal =
        new MonteisPrincipal(UUID.randomUUID(), USERNAME, readExperimentIds, writeExperimentIds);
    var authentication = new MonteisAuthenticationToken(null, principal, authorities);

    SecurityContext previous = SecurityContextHolder.getContext();
    try {
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      return action.get();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }
}
