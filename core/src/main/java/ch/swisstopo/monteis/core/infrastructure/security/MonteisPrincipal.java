package ch.swisstopo.monteis.core.infrastructure.security;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** The caller identity carried as an {@link org.springframework.security.core.Authentication} principal. */
public record MonteisPrincipal(UUID subject, String username, List<Long> experimentIds)
    implements Principal {

  public UUID getSubject() {
    return subject;
  }

  @Override
  public String getName() {
    return username;
  }

  public List<Long> getExperimentIds() {
    return experimentIds;
  }
}
