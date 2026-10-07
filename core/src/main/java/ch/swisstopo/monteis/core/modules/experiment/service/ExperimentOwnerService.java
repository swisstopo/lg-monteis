package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryAccessDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Owners are stored as Keycloak ids only. An owner counts as long as they are a PI of the
 * experiment in Keycloak, so someone who lost write access, was disabled or was deleted disappears
 * from every response right away, the stale row goes with the next save of the experiment or its
 * owners.
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

  /**
   * Never fails because of Keycloak: without it the experiment is still shown, with its owners
   * marked as unavailable.
   */
  public VisibleOwners visibleOwners(Experiment experiment) {
    return visibleOwners(experiment.getId(), experiment.getOwnerIds());
  }

  public Map<UUID, VisibleOwners> visibleOwners(Collection<Experiment> experiments) {
    Map<UUID, VisibleOwners> owners = new LinkedHashMap<>();
    for (Experiment experiment : experiments) {
      owners.put(experiment.getId(), visibleOwners(experiment));
    }
    return owners;
  }

  /** Every visible owner of a readable experiment, each once. Feeds the owner filter. */
  public List<DirectoryUser> assignedOwners() {
    return ownerQueryRepository.findOwnerIdsByExperiment().entrySet().stream()
        .flatMap(entry -> visibleOwners(entry.getKey(), entry.getValue()).users().stream())
        .collect(Collectors.toMap(DirectoryUser::id, Function.identity(), (first, _) -> first))
        .values()
        .stream()
        .sorted(DirectoryUser.BY_NAME)
        .toList();
  }

  /**
   * @throws UserDirectoryUnavailableException if Keycloak cannot be asked
   */
  public List<DirectoryUser> candidates(UUID experimentId) {
    repository.getById(experimentId);
    return userDirectory.principalInvestigatorsOf(experimentId);
  }

  /**
   * @throws FieldBusinessValidationException if one of the ids is not a PI of the experiment
   * @throws UserDirectoryUnavailableException if Keycloak cannot be asked
   */
  @AuditChanges
  public Experiment replaceOwners(UUID experimentId, Set<UUID> ownerIds) {
    Set<UUID> candidateIds =
        candidates(experimentId).stream().map(DirectoryUser::id).collect(Collectors.toSet());
    Set<UUID> notEligible = new HashSet<>(ownerIds);
    notEligible.removeAll(candidateIds);
    if (!notEligible.isEmpty()) {
      throw new FieldBusinessValidationException(
          OWNER_IDS_FIELD, notEligible, NOT_ELIGIBLE_KEY, Map.of());
    }
    return repository.replaceOwners(experimentId, ownerIds);
  }

  /**
   * Drops the stored owners that are no longer PIs. Best effort: without an answer from Keycloak
   * nothing is dropped, an owner must never go missing only because Keycloak could not be asked.
   */
  public Experiment dropFormerOwners(Experiment experiment) {
    if (experiment.getOwnerIds().isEmpty()) {
      return experiment;
    }
    return principalInvestigatorIds(experiment.getId())
        .map(piIds -> retainedOwners(experiment.getOwnerIds(), piIds))
        .filter(retained -> !retained.equals(experiment.getOwnerIds()))
        .map(retained -> repository.replaceOwners(experiment.getId(), retained))
        .orElse(experiment);
  }

  private Optional<Set<UUID>> principalInvestigatorIds(UUID experimentId) {
    try {
      return Optional.of(
          userDirectory.principalInvestigatorsOf(experimentId).stream()
              .map(DirectoryUser::id)
              .collect(Collectors.toSet()));
    } catch (UserDirectoryUnavailableException e) {
      log.warn("Keeping the owners of experiment {}: {}", experimentId, e.getMessage());
      return Optional.empty();
    }
  }

  private static Set<UUID> retainedOwners(Set<UUID> ownerIds, Set<UUID> piIds) {
    Set<UUID> retained = new HashSet<>(ownerIds);
    retained.retainAll(piIds);
    return Set.copyOf(retained);
  }

  private VisibleOwners visibleOwners(UUID experimentId, Set<UUID> ownerIds) {
    if (ownerIds.isEmpty()) {
      return VisibleOwners.NONE;
    }
    try {
      return new VisibleOwners(
          userDirectory.principalInvestigatorsOf(experimentId).stream()
              .filter(user -> ownerIds.contains(user.id()))
              .toList(),
          false);
    } catch (UserDirectoryAccessDeniedException e) {
      // the realm grants every experiment user view on the PIs, a denial is a setup problem
      log.warn("Hiding the owners of experiment {}: {}", experimentId, e.getMessage());
      return VisibleOwners.NONE;
    } catch (UserDirectoryUnavailableException e) {
      log.warn("Showing experiment {} without owners: {}", experimentId, e.getMessage());
      return VisibleOwners.UNAVAILABLE;
    }
  }
}
