package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Explicit, auditable opt-in for background jobs with no HTTP request/JWT (e.g. the startup audit
 * backfill) that still need elevated DB access. An unbound {@link SecurityContextHolder} must
 * never implicitly resolve to elevated access — callers bind this deliberately.
 */
public final class SystemSecurityContext {

  private static final UUID SYSTEM_SUBJECT =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  private static final Authentication SYSTEM =
      new MonteisAuthenticationToken(
          null,
          new MonteisPrincipal(SYSTEM_SUBJECT, "SYSTEM", List.of()),
          List.of(
              new SimpleGrantedAuthority(MonteisJwtAuthenticationConverter.READ_ALL_AUTHORITY)));

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
}
