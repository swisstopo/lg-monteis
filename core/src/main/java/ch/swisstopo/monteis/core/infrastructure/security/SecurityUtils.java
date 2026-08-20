package ch.swisstopo.monteis.core.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

  private SecurityUtils() {}

  /**
   * Retrieves the current MonteisPrincipal from the SecurityContext.
   */
  public static String getCurrentUserHandle() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof MonteisPrincipal principal) {
      return principal.getSubject().toString();
    }
    return null;
  }
}
