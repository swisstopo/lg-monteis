package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
  public String getCurrentUserHandle() {
    return currentPrincipal().map(principal -> principal.getSubject().toString()).orElse(null);
  }

  /**
   * Returns the username of the authenticated caller, for records that show who made them.
   *
   * @throws IllegalStateException without an authenticated {@link MonteisPrincipal}. the filter
   *     chain authenticates every write, so getting here without one is a bug, not a user error
   */
  public String requireCurrentUsername() {
    return currentPrincipal()
        .map(MonteisPrincipal::getName)
        .orElseThrow(() -> new IllegalStateException("No authenticated MonteisPrincipal"));
  }

  public Optional<UUID> currentSubject() {
    return currentPrincipal().map(MonteisPrincipal::getSubject);
  }

  public Optional<String> currentAccessToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
      return Optional.of(jwt.getTokenValue());
    }
    return Optional.empty();
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
