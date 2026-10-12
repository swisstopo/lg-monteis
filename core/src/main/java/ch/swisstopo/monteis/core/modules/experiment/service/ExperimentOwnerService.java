package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
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

  public VisibleOwners findOwnersOf(Experiment experiment) {
    return findOwnersByExperiment(List.of(experiment)).get(experiment.getId());
  }

  public Map<UUID, VisibleOwners> findOwnersByExperiment(List<Experiment> experiments) {
    return visibleOwnersByExperiment(
        experiments.stream().map(ExperimentOwnerService::storedOwnersOf).toList());
  }

  public List<DirectoryUser> findAllVisibleOwners() {
    return visibleOwnersByExperiment(ownerQueryRepository.findStoredOwners()).values().stream()
        .flatMap(owners -> owners.users().stream())
        .distinct()
        .sorted(DirectoryUser.BY_NAME)
        .toList();
  }

  public Map<UUID, VisibleOwners> ownersByExperimentForExport() {
    return visibleOwnersByExperiment(ownerQueryRepository.findStoredOwners());
  }

  private Map<UUID, VisibleOwners> visibleOwnersByExperiment(List<StoredOwners> storedOwners) {
    Map<UUID, Pis> pisByExperiment =
        userDirectory.pisForReading(idsOfExperimentsWithOwners(storedOwners));
    Map<UUID, VisibleOwners> visibleOwners = new LinkedHashMap<>();
    for (StoredOwners owners : storedOwners) {
      UUID experimentId = owners.experimentId();
      visibleOwners.put(experimentId, visibleOwnersOf(owners, pisByExperiment.get(experimentId)));
    }
    return visibleOwners;
  }

  private static List<UUID> idsOfExperimentsWithOwners(List<StoredOwners> storedOwners) {
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
      case Known(var users) -> ownersAmong(users, storedOwners);
      case NoWriteGroup() ->
          hideOwners(experimentId, VisibleOwners.NO_WRITE_GROUP, "no write group");
      case AccessDenied(var reason) ->
          hideOwners(experimentId, VisibleOwners.ACCESS_DENIED, reason);
      case KeycloakUnavailable(var _) -> VisibleOwners.KEYCLOAK_UNAVAILABLE;
    };
  }

  private static VisibleOwners ownersAmong(List<DirectoryUser> pis, StoredOwners storedOwners) {
    return VisibleOwners.of(pis.stream().filter(pi -> storedOwners.isOwner(pi.id())).toList());
  }

  private static VisibleOwners hideOwners(UUID experimentId, VisibleOwners hidden, String reason) {
    log.warn("Hiding the owners of experiment {}: {}", experimentId, reason);
    return hidden;
  }

  private static StoredOwners storedOwnersOf(Experiment experiment) {
    return new StoredOwners(experiment.getId(), experiment.getOwnerIds());
  }

  public List<DirectoryUser> findOwnerCandidates(UUID experimentId) {
    repository.requireVisible(experimentId);
    return currentPisOrFail(experimentId);
  }

  public Experiment replaceOwnersInKeycloak(Experiment experiment, Set<UUID> ownerIds) {
    UUID experimentId = experiment.getId();
    return switch (userDirectory.pisForWriting(experimentId)) {
      case Known(var pis) -> replaceOwnersAmong(experimentId, ownerIds, pis);
      case NoWriteGroup() -> replaceOwnersAmong(experimentId, ownerIds, List.of());
      case AccessDenied(var reason) -> keepOwners(experiment, reason);
      case KeycloakUnavailable(var reason) -> keepOwners(experiment, reason);
    };
  }

  private List<DirectoryUser> currentPisOrFail(UUID experimentId) {
    return switch (userDirectory.pisForWriting(experimentId)) {
      case Known(var pis) -> pis;
      case NoWriteGroup() -> List.of();
      case AccessDenied(var reason) -> throw new UserDirectoryDeniedException(reason);
      case KeycloakUnavailable(var reason) -> throw new UserDirectoryUnavailableException(reason);
    };
  }

  private Experiment replaceOwnersAmong(
      UUID experimentId, Set<UUID> ownerIds, List<DirectoryUser> pis) {
    rejectOwnersWhoAreNotPis(ownerIds, pis);
    return repository.replaceOwners(experimentId, ownerIds);
  }

  private static Experiment keepOwners(Experiment experiment, String reason) {
    log.warn("Keeping the owners of experiment {}: {}", experiment.getId(), reason);
    return experiment;
  }

  private static void rejectOwnersWhoAreNotPis(Set<UUID> ownerIds, List<DirectoryUser> pis) {
    Set<UUID> notPis = idsWithout(ownerIds, userIds(pis));
    if (!notPis.isEmpty()) {
      throw new FieldBusinessValidationException(
          OWNER_IDS_FIELD, notPis, NOT_ELIGIBLE_KEY, Map.of());
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
