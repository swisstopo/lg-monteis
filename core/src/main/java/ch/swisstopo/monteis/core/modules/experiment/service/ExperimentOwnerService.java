package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.AccessDenied;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.KeycloakUnavailable;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.Known;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.NoWriteGroup;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Owners are stored as Keycloak ids only and have to be PIs of the experiment. A caller sees an
 * owner only as long as they are a PI, reading never fails because of Keycloak and the {@link
 * VisibleOwners} say why owners are missing. Someone who stopped being a PI goes with the next
 * save of the experiment or its owners, and only a complete answer from Keycloak ({@link Known})
 * ever removes anyone.
 */
@Service
public class ExperimentOwnerService {

  public static final String OWNER_IDS_FIELD = "ownerIds";
  public static final String NOT_ELIGIBLE_KEY = "experiment.owner.not-eligible";

  private static final Logger log = LoggerFactory.getLogger(ExperimentOwnerService.class);

  private final ExperimentRepository repository;
  private final ExperimentOwnerQueryRepository ownerQueryRepository;
  private final UserDirectory userDirectory;

  public ExperimentOwnerService(
      ExperimentRepository repository,
      ExperimentOwnerQueryRepository ownerQueryRepository,
      UserDirectory userDirectory) {
    this.repository = repository;
    this.ownerQueryRepository = ownerQueryRepository;
    this.userDirectory = userDirectory;
  }

  public ExperimentWithOwners withOwners(Experiment experiment) {
    return withOwners(List.of(experiment)).getFirst();
  }

  public List<ExperimentWithOwners> withOwners(List<Experiment> experiments) {
    Map<UUID, VisibleOwners> owners =
        visibleOwnersOf(experiments.stream().map(ExperimentOwnerService::storedOwnersOf).toList());
    return experiments.stream()
        .map(experiment -> new ExperimentWithOwners(experiment, owners.get(experiment.getId())))
        .toList();
  }

  public PagedResult<ExperimentWithOwners> withOwners(PagedResult<Experiment> page) {
    return new PagedResult<>(withOwners(page.rows()), page.totalCount());
  }

  /** Every visible owner of a readable experiment, each once, sorted by name. */
  public List<DirectoryUser> filterableOwners() {
    return visibleOwnersOf(ownerQueryRepository.findStoredOwners()).values().stream()
        .flatMap(owners -> owners.users().stream())
        .distinct()
        .sorted(DirectoryUser.BY_NAME)
        .toList();
  }

  /**
   * The visible owners of every readable experiment that has owners, resolved before a CSV export
   * streams its rows. Experiments missing here have no owners.
   */
  public Map<UUID, VisibleOwners> ownersForExport() {
    return visibleOwnersOf(ownerQueryRepository.findStoredOwners());
  }

  private Map<UUID, VisibleOwners> visibleOwnersOf(List<StoredOwners> storedOwners) {
    Map<UUID, Pis> pisByExperiment =
        userDirectory.pisForReading(experimentsWithOwners(storedOwners));
    Map<UUID, VisibleOwners> visibleOwners = new LinkedHashMap<>();
    for (StoredOwners owners : storedOwners) {
      UUID experimentId = owners.experimentId();
      visibleOwners.put(experimentId, visibleOwnersOf(owners, pisByExperiment.get(experimentId)));
    }
    return visibleOwners;
  }

  private static List<UUID> experimentsWithOwners(List<StoredOwners> storedOwners) {
    return storedOwners.stream()
        .filter(StoredOwners::hasOwners)
        .map(StoredOwners::experimentId)
        .toList();
  }

  private static VisibleOwners visibleOwnersOf(StoredOwners storedOwners, Pis pis) {
    if (!storedOwners.hasOwners()) {
      return VisibleOwners.NONE;
    }
    UUID experimentId = storedOwners.experimentId();
    return switch (pis) {
      case Known(var users) -> onlyOwnersAmong(users, storedOwners);
      case NoWriteGroup() ->
          hideOwners(experimentId, VisibleOwners.NO_WRITE_GROUP, "no write group");
      case AccessDenied(var reason) ->
          hideOwners(experimentId, VisibleOwners.ACCESS_DENIED, reason);
      case KeycloakUnavailable(var _) -> VisibleOwners.KEYCLOAK_UNAVAILABLE;
    };
  }

  private static VisibleOwners onlyOwnersAmong(List<DirectoryUser> pis, StoredOwners storedOwners) {
    return VisibleOwners.of(pis.stream().filter(pi -> storedOwners.isOwner(pi.id())).toList());
  }

  private static VisibleOwners hideOwners(UUID experimentId, VisibleOwners hidden, String reason) {
    log.warn("Hiding the owners of experiment {}: {}", experimentId, reason);
    return hidden;
  }

  private static StoredOwners storedOwnersOf(Experiment experiment) {
    return new StoredOwners(experiment.getId(), experiment.getOwnerIds());
  }

  /**
   * The current PIs. Without a write group there is no one to pick yet.
   *
   * @throws UserDirectoryDeniedException if Keycloak refuses
   * @throws UserDirectoryUnavailableException if Keycloak cannot be asked
   */
  public List<DirectoryUser> ownerCandidates(UUID experimentId) {
    repository.requireVisible(experimentId);
    return currentPisOrFail(experimentId);
  }

  /**
   * @throws FieldBusinessValidationException if one of the ids is not a PI of the experiment
   * @throws UserDirectoryDeniedException if Keycloak refuses
   * @throws UserDirectoryUnavailableException if Keycloak cannot be asked
   */
  @AuditChanges
  public Experiment replaceOwners(UUID experimentId, Set<UUID> ownerIds) {
    rejectOwnersWhoAreNoPis(ownerIds, ownerCandidates(experimentId));
    return repository.replaceOwners(experimentId, ownerIds);
  }

  /** Best effort after a save, never fails and keeps every owner if Keycloak can't tell. */
  @AuditChanges
  public Experiment dropFormerOwners(Experiment experiment) {
    Set<UUID> formerOwners = formerOwnersOf(experiment);
    if (formerOwners.isEmpty()) {
      return experiment;
    }
    return removeOwnersQuietly(experiment, formerOwners);
  }

  private List<DirectoryUser> currentPisOrFail(UUID experimentId) {
    return switch (userDirectory.pisForWriting(experimentId)) {
      case Known(var pis) -> pis;
      case NoWriteGroup() -> List.of();
      case AccessDenied(var reason) -> throw new UserDirectoryDeniedException(reason);
      case KeycloakUnavailable(var reason) -> throw new UserDirectoryUnavailableException(reason);
    };
  }

  private static void rejectOwnersWhoAreNoPis(Set<UUID> ownerIds, List<DirectoryUser> pis) {
    Set<UUID> noPis = idsWithout(ownerIds, userIds(pis));
    if (!noPis.isEmpty()) {
      throw new FieldBusinessValidationException(
          OWNER_IDS_FIELD, noPis, NOT_ELIGIBLE_KEY, Map.of());
    }
  }

  /** Empty unless Keycloak knows the PIs, an owner never goes because Keycloak can't tell. */
  private Set<UUID> formerOwnersOf(Experiment experiment) {
    if (experiment.getOwnerIds().isEmpty()) {
      return Set.of();
    }
    if (userDirectory.pisForWriting(experiment.getId()) instanceof Known(var pis)) {
      return idsWithout(experiment.getOwnerIds(), userIds(pis));
    }
    return Set.of();
  }

  private Experiment removeOwnersQuietly(Experiment experiment, Set<UUID> formerOwners) {
    Set<UUID> remainingOwners = idsWithout(experiment.getOwnerIds(), formerOwners);
    try {
      return repository.replaceOwners(experiment.getId(), remainingOwners);
    } catch (RuntimeException e) {
      log.warn("Keeping the former owners of experiment {}: {}", experiment.getId(), e.toString());
      return experiment;
    }
  }

  private static Set<UUID> userIds(List<DirectoryUser> users) {
    return users.stream().map(DirectoryUser::id).collect(Collectors.toSet());
  }

  private static Set<UUID> idsWithout(Set<UUID> ids, Set<UUID> removed) {
    Set<UUID> remaining = new HashSet<>(ids);
    remaining.removeAll(removed);
    return Set.copyOf(remaining);
  }
}
