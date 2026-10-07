package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.javers.AuditChanges;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PiLookup;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnerQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentOwnership;
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
 * owners. Only a complete answer from Keycloak ({@link PiLookup.Found}) ever removes anyone.
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

  /** For one experiment. Never fails because of Keycloak. */
  public VisibleOwners ownersOf(Experiment experiment) {
    return resolver().ownersOf(ownershipOf(experiment));
  }

  /** For a page of experiments, in their order. Stops asking Keycloak once it is unavailable. */
  public Map<UUID, VisibleOwners> ownersByExperiment(Collection<Experiment> experiments) {
    OwnerResolver resolver = resolver();
    Map<UUID, VisibleOwners> owners = new LinkedHashMap<>();
    for (Experiment experiment : experiments) {
      owners.put(experiment.getId(), resolver.ownersOf(ownershipOf(experiment)));
    }
    return owners;
  }

  /** Every visible owner of a readable experiment, each once, sorted by name. Feeds the filter. */
  public List<DirectoryUser> filterableOwners() {
    OwnerResolver resolver = resolver();
    Set<DirectoryUser> owners = new TreeSet<>(DirectoryUser.BY_NAME);
    for (ExperimentOwnership ownership : ownerQueryRepository.findOwnerships()) {
      owners.addAll(resolver.ownersOf(ownership).users());
    }
    return List.copyOf(owners);
  }

  /**
   * For a run over many experiments, e.g. a CSV export: resolves one after the other and gives up
   * on Keycloak once it is unavailable.
   */
  public Function<ExperimentOwnership, VisibleOwners> ownerResolver() {
    return resolver()::ownersOf;
  }

  private OwnerResolver resolver() {
    return new OwnerResolver(userDirectory);
  }

  /**
   * The current PIs, asked fresh. A missing write group means there is no one to pick yet.
   *
   * @throws UserDirectoryDeniedException if Keycloak refuses
   * @throws UserDirectoryUnavailableException if Keycloak cannot be asked
   */
  public List<DirectoryUser> candidates(UUID experimentId) {
    repository.requireVisible(experimentId);
    return switch (userDirectory.lookupPisFresh(experimentId)) {
      case PiLookup.Found found -> found.pis();
      case PiLookup.NoWriteGroup _ -> List.of();
      case PiLookup.Denied denied -> throw new UserDirectoryDeniedException(denied.reason());
      case PiLookup.Unavailable unavailable ->
          throw new UserDirectoryUnavailableException(unavailable.reason());
    };
  }

  /**
   * @throws FieldBusinessValidationException if one of the ids is not a PI of the experiment
   * @throws UserDirectoryDeniedException if Keycloak refuses
   * @throws UserDirectoryUnavailableException if Keycloak cannot be asked
   */
  @AuditChanges
  public Experiment replaceOwners(UUID experimentId, Set<UUID> ownerIds) {
    Set<UUID> candidateIds = ids(candidates(experimentId));
    Set<UUID> notEligible = new HashSet<>(ownerIds);
    notEligible.removeAll(candidateIds);
    if (!notEligible.isEmpty()) {
      throw new FieldBusinessValidationException(
          OWNER_IDS_FIELD, notEligible, NOT_ELIGIBLE_KEY, Map.of());
    }
    return repository.replaceOwners(experimentId, ownerIds);
  }

  /**
   * Drops the stored owners that are no longer PIs. Best effort after a save: anything short of a
   * complete answer from Keycloak keeps every owner, and a failure is only logged.
   */
  @AuditChanges
  public Experiment dropFormerOwners(Experiment experiment) {
    if (experiment.getOwnerIds().isEmpty()
        || !(userDirectory.lookupPisFresh(experiment.getId()) instanceof PiLookup.Found found)) {
      return experiment;
    }
    Set<UUID> retained = new HashSet<>(experiment.getOwnerIds());
    retained.retainAll(ids(found.pis()));
    if (retained.equals(experiment.getOwnerIds())) {
      return experiment;
    }
    try {
      return repository.replaceOwners(experiment.getId(), retained);
    } catch (RuntimeException e) {
      log.warn("Keeping the former owners of experiment {}: {}", experiment.getId(), e.toString());
      return experiment;
    }
  }

  private static ExperimentOwnership ownershipOf(Experiment experiment) {
    return new ExperimentOwnership(experiment.getId(), experiment.getOwnerIds());
  }

  private static Set<UUID> ids(List<DirectoryUser> users) {
    return users.stream().map(DirectoryUser::id).collect(Collectors.toSet());
  }

  /**
   * Resolves the visible owners of one experiment after the other. Once Keycloak is unavailable
   * it is not asked again, every further experiment gets {@link VisibleOwners#UNAVAILABLE} right
   * away instead of waiting for another timeout.
   */
  private static final class OwnerResolver {

    private final UserDirectory userDirectory;
    private boolean keycloakUnavailable;

    private OwnerResolver(UserDirectory userDirectory) {
      this.userDirectory = userDirectory;
    }

    VisibleOwners ownersOf(ExperimentOwnership ownership) {
      if (!ownership.hasOwners()) {
        return VisibleOwners.NONE;
      }
      if (keycloakUnavailable) {
        return VisibleOwners.UNAVAILABLE;
      }
      UUID experimentId = ownership.experimentId();
      return switch (userDirectory.lookupPis(experimentId)) {
        case PiLookup.Found found ->
            VisibleOwners.of(
                found.pis().stream().filter(pi -> ownership.isOwner(pi.id())).toList());
        case PiLookup.NoWriteGroup _ -> {
          log.warn("Experiment {} has owners but no write group", experimentId);
          yield VisibleOwners.NONE;
        }
        case PiLookup.Denied denied -> {
          log.warn("Hiding the owners of experiment {}: {}", experimentId, denied.reason());
          yield VisibleOwners.NONE;
        }
        case PiLookup.Unavailable unavailable -> {
          log.warn("Showing experiments without owners: {}", unavailable.reason());
          keycloakUnavailable = true;
          yield VisibleOwners.UNAVAILABLE;
        }
      };
    }
  }
}
