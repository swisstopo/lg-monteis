package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.AccessDenied;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.KeycloakUnavailable;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.Known;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.NoWriteGroup;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Which owners a caller gets to see. An owner counts as long as they are a PI of the experiment in
 * Keycloak, so someone who lost write access, was disabled or was deleted is not shown anymore.
 * Never fails because of Keycloak, the visible owners say why they are missing.
 */
@Service
public class ExperimentOwnerQueries {

  private static final Logger log = LoggerFactory.getLogger(ExperimentOwnerQueries.class);

  private final ExperimentOwnerQueryRepository ownerQueryRepository;
  private final UserDirectory userDirectory;

  public ExperimentOwnerQueries(
      ExperimentOwnerQueryRepository ownerQueryRepository, UserDirectory userDirectory) {
    this.ownerQueryRepository = ownerQueryRepository;
    this.userDirectory = userDirectory;
  }

  public ExperimentWithOwners withOwners(Experiment experiment) {
    return new Resolver().withOwners(experiment);
  }

  /** Asks Keycloak no more once it is unavailable. */
  public List<ExperimentWithOwners> withOwners(List<Experiment> experiments) {
    Resolver resolver = new Resolver();
    return experiments.stream().map(resolver::withOwners).toList();
  }

  /** Asks Keycloak no more once it is unavailable. */
  public PagedResult<ExperimentWithOwners> withOwners(PagedResult<Experiment> page) {
    return new PagedResult<>(withOwners(page.rows()), page.totalCount());
  }

  /** Every visible owner of a readable experiment, each once, sorted by name. */
  public List<DirectoryUser> filterableOwners() {
    Resolver resolver = new Resolver();
    Set<DirectoryUser> owners = new TreeSet<>(DirectoryUser.BY_NAME);
    for (StoredOwners storedOwners : ownerQueryRepository.findStoredOwners()) {
      owners.addAll(resolver.ownersOf(storedOwners).users());
    }
    return List.copyOf(owners);
  }

  /** For one CSV export, asks Keycloak no more once it is unavailable. */
  public Function<StoredOwners, VisibleOwners> ownersForExport() {
    return new Resolver()::ownersOf;
  }

  /**
   * Resolves one experiment after the other. Once Keycloak is unavailable it is not asked again,
   * so a page or an export waits for one timeout and not one per row.
   */
  private final class Resolver {

    private boolean keycloakUnavailable;

    ExperimentWithOwners withOwners(Experiment experiment) {
      return new ExperimentWithOwners(experiment, ownersOf(storedOwnersOf(experiment)));
    }

    VisibleOwners ownersOf(StoredOwners storedOwners) {
      if (!storedOwners.hasOwners()) {
        return VisibleOwners.NONE;
      }
      if (keycloakUnavailable) {
        return VisibleOwners.UNAVAILABLE;
      }
      UUID experimentId = storedOwners.experimentId();
      return switch (userDirectory.pisForReading(experimentId)) {
        case Known(var pis) -> onlyOwnersAmong(pis, storedOwners);
        case NoWriteGroup() -> hideOwners(experimentId, "the experiment has no write group");
        case AccessDenied(var reason) -> hideOwners(experimentId, reason);
        case KeycloakUnavailable(var reason) -> giveUpOnKeycloak(reason);
      };
    }

    private VisibleOwners giveUpOnKeycloak(String reason) {
      log.warn("Showing experiments without owners: {}", reason);
      keycloakUnavailable = true;
      return VisibleOwners.UNAVAILABLE;
    }
  }

  private static VisibleOwners onlyOwnersAmong(List<DirectoryUser> pis, StoredOwners storedOwners) {
    return VisibleOwners.of(pis.stream().filter(pi -> storedOwners.isOwner(pi.id())).toList());
  }

  private static VisibleOwners hideOwners(UUID experimentId, String reason) {
    log.warn("Hiding the owners of experiment {}: {}", experimentId, reason);
    return VisibleOwners.NONE;
  }

  private static StoredOwners storedOwnersOf(Experiment experiment) {
    return new StoredOwners(experiment.getId(), experiment.getOwnerIds());
  }
}
