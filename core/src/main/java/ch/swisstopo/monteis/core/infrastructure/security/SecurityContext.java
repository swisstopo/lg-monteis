package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Set;

/**
 * The resolved caller identity for the duration of a request, carried via {@link #CURRENT}. Read
 * by {@link ch.swisstopo.monteis.core.infrastructure.jooq.RlsConnectionProvider} to propagate the
 * security context into the database session as GUCs for row-level security. Nothing outside
 * {@code infrastructure.security} and {@code infrastructure.jooq} should need to touch this.
 */
public record SecurityContext(AccessLevel accessLevel, Set<Long> experimentIds) {

  /**
   * NONE unconditionally means no access: RLS on the database side only checks admin-or-member,
   * never the access level itself, so a NONE paired with a non-empty experimentIds would
   * silently grant USER-equivalent visibility.
   */
  public SecurityContext {
    if (accessLevel == AccessLevel.NONE) {
      experimentIds = Set.of();
    }
  }

  /** Fail-closed default: no access. Never used as an implicit bypass. */
  public static final SecurityContext DENY_ALL = new SecurityContext(AccessLevel.NONE, Set.of());

  public static final ScopedValue<SecurityContext> CURRENT = ScopedValue.newInstance();

  public static SecurityContext current() {
    return CURRENT.orElse(DENY_ALL);
  }
}
