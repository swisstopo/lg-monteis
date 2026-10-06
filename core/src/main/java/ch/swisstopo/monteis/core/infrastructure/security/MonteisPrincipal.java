package ch.swisstopo.monteis.core.infrastructure.security;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * The caller identity carried as an {@link org.springframework.security.core.Authentication}
 * principal.
 *
 * <p>{@code writeExperimentIds} is always a subset of {@code readExperimentIds}: Keycloak maps
 * write_experiment_ids into read_experiment_ids too, so a write id outside the read ids means a
 * tampered or misconfigured token, and it is dropped rather than trusted.
 */
public record MonteisPrincipal(
    UUID subject, String username, List<UUID> readExperimentIds, List<UUID> writeExperimentIds)
    implements Principal {

  public MonteisPrincipal {
    readExperimentIds = List.copyOf(readExperimentIds);
    writeExperimentIds = writeExperimentIds.stream().filter(readExperimentIds::contains).toList();
  }

  public UUID getSubject() {
    return subject;
  }

  @Override
  public String getName() {
    return username;
  }

  public List<UUID> getReadExperimentIds() {
    return readExperimentIds;
  }

  public List<UUID> getWriteExperimentIds() {
    return writeExperimentIds;
  }
}
