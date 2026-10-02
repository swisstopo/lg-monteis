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

  public String getCurrentUsername() {
    return currentPrincipal().map(MonteisPrincipal::getName).orElse(null);
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
