package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.AccessDenied;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.KeycloakUnavailable;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.Known;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.NoWriteGroup;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * owners. Only a complete answer from Keycloak ({@link Known}) ever removes anyone.
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

  /** Never fails because of Keycloak. */
  public VisibleOwners ownersOf(Experiment experiment) {
    return new VisibleOwnersResolver(userDirectory).ownersOf(storedOwnersOf(experiment));
  }

  /** In the order of the experiments, asks Keycloak no more once it is unavailable. */
  public Map<UUID, VisibleOwners> ownersByExperiment(Collection<Experiment> experiments) {
    VisibleOwnersResolver resolver = new VisibleOwnersResolver(userDirectory);
    Map<UUID, VisibleOwners> ownersByExperiment = new LinkedHashMap<>();
    for (Experiment experiment : experiments) {
      ownersByExperiment.put(experiment.getId(), resolver.ownersOf(storedOwnersOf(experiment)));
    }
    return ownersByExperiment;
  }

  /** Every visible owner of a readable experiment, each once, sorted by name. */
  public List<DirectoryUser> filterableOwners() {
    VisibleOwnersResolver resolver = new VisibleOwnersResolver(userDirectory);
    Set<DirectoryUser> owners = new TreeSet<>(DirectoryUser.BY_NAME);
    for (StoredOwners storedOwners : ownerQueryRepository.findStoredOwners()) {
      owners.addAll(resolver.ownersOf(storedOwners).users());
    }
    return List.copyOf(owners);
  }

  /** For one CSV export, asks Keycloak no more once it is unavailable. */
  public Function<StoredOwners, VisibleOwners> ownersForExport() {
    return new VisibleOwnersResolver(userDirectory)::ownersOf;
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
    return switch (userDirectory.currentPrincipalInvestigatorsOf(experimentId)) {
      case Known known -> known.users();
      case NoWriteGroup _ -> List.of();
      case AccessDenied denied -> throw new UserDirectoryDeniedException(denied.reason());
      case KeycloakUnavailable unavailable ->
          throw new UserDirectoryUnavailableException(unavailable.reason());
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
    PrincipalInvestigators pis = userDirectory.currentPrincipalInvestigatorsOf(experiment.getId());
    if (pis instanceof Known known) {
      return idsWithout(experiment.getOwnerIds(), userIds(known.users()));
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

  private static StoredOwners storedOwnersOf(Experiment experiment) {
    return new StoredOwners(experiment.getId(), experiment.getOwnerIds());
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
