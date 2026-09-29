package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Explicit, auditable opt-in for background jobs with no HTTP request/JWT (e.g. the startup audit
 * backfill) that still need all-experiment read access. An unbound {@link SecurityContextHolder}
 * must never implicitly resolve to elevated access — callers bind this deliberately.
 *
 * <p>The bound authentication carries no authority. {@link AccessPolicy} recognises it by identity
 * and answers {@link Capabilities#SYSTEM}, so no request token can ever produce it (BR4.7).
 */
public final class SystemSecurityContext {

  private static final UUID SYSTEM_SUBJECT =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  private static final Authentication SYSTEM =
      new MonteisAuthenticationToken(
          null, new MonteisPrincipal(SYSTEM_SUBJECT, "SYSTEM", List.of(), List.of()), List.of());

  private SystemSecurityContext() {}

  public static void runAsSystem(Runnable action) {
    SecurityContext previous = SecurityContextHolder.getContext();
    try {
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(SYSTEM);
      SecurityContextHolder.setContext(context);
      action.run();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }

  /** Whether {@code authentication} is the one {@link #runAsSystem} binds. */
  static boolean isSystemAuthentication(Authentication authentication) {
    return authentication == SYSTEM;
  }
}
