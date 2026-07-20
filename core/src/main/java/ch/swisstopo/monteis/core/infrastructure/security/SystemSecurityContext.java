package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Set;

/**
 * Explicit, auditable opt-in for background/system jobs (e.g. the startup audit backfill) that
 * need database access despite having no HTTP request or JWT to derive a {@link SecurityContext}
 * from. Callers must bind this deliberately at their own entry point — an unbound
 * {@link SecurityContext} must never implicitly resolve to elevated access.
 */
public final class SystemSecurityContext {

  private static final SecurityContext SYSTEM = new SecurityContext(AccessLevel.ADMIN, Set.of());

  private SystemSecurityContext() {}

  public static void runAsSystem(Runnable action) {
    ScopedValue.where(SecurityContext.CURRENT, SYSTEM).run(action);
  }
}
