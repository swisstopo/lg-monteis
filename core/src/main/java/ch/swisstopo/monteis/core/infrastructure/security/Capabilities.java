package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Set;
import java.util.UUID;

/**
 * What a caller may do: the single answer every enforcement point asks for (contract C2). Derived
 * by {@link AccessPolicy} from the caller's {@code Authentication}; never persisted.
 *
 * @param canReadAllExperiments {@code api:admin} or {@code api:experiment:write-all}
 * @param canWriteAllExperiments same condition as {@code canReadAllExperiments}
 * @param readableExperimentIds the experiments the caller may read; empty when {@code
 *     canReadAllExperiments}
 * @param editableExperimentIds the experiments whose metadata the caller may edit; a subset of
 *     {@code readableExperimentIds}, empty when {@code canWriteAllExperiments}
 * @param canCreateExperiment {@code api:admin}
 * @param canManageSensors {@code api:admin}
 * @param canAccessDocuments {@code api:documents:read}
 * @param canUseAdminFunctions {@code api:admin}
 * @param isAdmin {@code api:admin}
 */
public record Capabilities(
    boolean canReadAllExperiments,
    boolean canWriteAllExperiments,
    Set<UUID> readableExperimentIds,
    Set<UUID> editableExperimentIds,
    boolean canCreateExperiment,
    boolean canManageSensors,
    boolean canAccessDocuments,
    boolean canUseAdminFunctions,
    boolean isAdmin) {

  /** Unauthenticated or unrecognised caller: nothing at all (fail closed). */
  public static final Capabilities NONE =
      new Capabilities(false, false, Set.of(), Set.of(), false, false, false, false, false);

  /**
   * Background jobs bound by {@link SystemSecurityContext#runAsSystem}: all-experiment read and
   * nothing else, like the system pseudo-user before MON-196.
   */
  public static final Capabilities SYSTEM =
      new Capabilities(true, false, Set.of(), Set.of(), false, false, false, false, false);

  public Capabilities {
    readableExperimentIds = Set.copyOf(readableExperimentIds);
    editableExperimentIds = Set.copyOf(editableExperimentIds);
  }

  /** {@code canReadAllExperiments || readableExperimentIds.contains(id)}; a null id is never readable. */
  public boolean canReadExperiment(UUID experimentId) {
    return experimentId != null
        && (canReadAllExperiments || readableExperimentIds.contains(experimentId));
  }

  /** {@code canWriteAllExperiments || editableExperimentIds.contains(id)}; a null id is never editable. */
  public boolean canEditExperiment(UUID experimentId) {
    return experimentId != null
        && (canWriteAllExperiments || editableExperimentIds.contains(experimentId));
  }
}
