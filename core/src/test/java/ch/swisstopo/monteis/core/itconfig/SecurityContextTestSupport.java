package ch.swisstopo.monteis.core.itconfig;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Binds an {@link org.springframework.security.core.Authentication} for the duration of a test
 * action, so integration tests that call repositories directly (bypassing the HTTP filter chain)
 * exercise the same row-level security policies a real request would.
 */
public final class SecurityContextTestSupport {

  /** The username every bound principal carries, to assert who a row was created by. */
  public static final String USERNAME = "test";

  private SecurityContextTestSupport() {}

  private static final List<String> ADMIN = List.of("experiment:read:all", "experiment:write:all");

  /** {@code experiment:read:all} and {@code experiment:write:all}: every experiment. */
  public static void runAsAdmin(Runnable action) {
    runAs(ADMIN, List.of(), List.of(), action);
  }

  /** {@code experiment:read} on {@code readExperimentIds} only. */
  public static void runAsUser(List<UUID> readExperimentIds, Runnable action) {
    runAs(List.of("experiment:read"), readExperimentIds, List.of(), action);
  }

  /**
   * {@code experiment:read} on {@code readExperimentIds} and {@code writeExperimentIds}, plus {@code
   * experiment:write} on {@code writeExperimentIds}, as the converter grants them.
   */
  public static void runAsUser(
      List<UUID> readExperimentIds, List<UUID> writeExperimentIds, Runnable action) {
    runAs(
        List.of("experiment:read", "experiment:write"),
        Stream.concat(readExperimentIds.stream(), writeExperimentIds.stream()).toList(),
        writeExperimentIds,
        action);
  }

  /** {@link #runAsAdmin} for an action with a result. */
  public static <T> T callAsAdmin(Supplier<T> action) {
    return callAs(ADMIN, List.of(), List.of(), action);
  }

  public static void runAs(
      List<String> permissions,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds,
      Runnable action) {
    callAs(
        permissions,
        readExperimentIds,
        writeExperimentIds,
        () -> {
          action.run();
          return null;
        });
  }

  public static <T> T callAs(
      List<String> permissions,
      List<UUID> readExperimentIds,
      List<UUID> writeExperimentIds,
      Supplier<T> action) {
    MonteisPrincipal principal = new MonteisPrincipal(UUID.randomUUID(), USERNAME);
    return callAs(
        new MonteisAuthenticationToken(
            null,
            principal,
            permissions.stream().map(SimpleGrantedAuthority::new).toList(),
            Set.copyOf(readExperimentIds),
            Set.copyOf(writeExperimentIds)),
        action);
  }

  public static <T> T callAs(Authentication authentication, Supplier<T> action) {
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
