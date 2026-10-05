package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
  public String getCurrentUserHandle() {
    return currentPrincipal().map(principal -> principal.getSubject().toString()).orElse(null);
  }

  /**
   * The username of the authenticated caller, for records that show who made them.
   *
   * @throws IllegalStateException without an authenticated MonteisPrincipal: the filter chain
   *     authenticates every write, so this is a programming error, not a user error
   */
  public String requireCurrentUsername() {
    return currentPrincipal()
        .map(MonteisPrincipal::getName)
        .orElseThrow(() -> new IllegalStateException("No authenticated MonteisPrincipal"));
  }

  private static Optional<MonteisPrincipal> currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof MonteisPrincipal principal) {
      return Optional.of(principal);
    }
    return Optional.empty();
  }
}
