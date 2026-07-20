package ch.swisstopo.monteis.core.infrastructure.security;

/**
 * Access level resolved from the caller's JWT for the duration of a request. {@link #NONE} is
 * the fail-closed default when no security context is bound.
 *
 * <p>Each constant owns the exact role-claim string it corresponds to on the JWT (via {@link
 * #toString()}), so callers like {@code SecurityContextFilter} only ever need to work with
 * {@code AccessLevel} itself — never a separate, hand-kept string constant that could drift out
 * of sync. If the real JWT's role naming turns out to differ from the enum constant names below,
 * only the constructor argument here needs to change.
 */
public enum AccessLevel {
  ADMIN("ADMIN"), // IMPORTANT: roleName must be in sync with is_Admin() function on psql table!
  USER("USER"),
  NONE("NONE");

  private final String roleName;

  AccessLevel(String roleName) {
    this.roleName = roleName;
  }

  @Override
  public String toString() {
    return roleName;
  }
}
