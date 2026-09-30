package ch.swisstopo.monteis.core.infrastructure.security;

import java.util.Set;
import java.util.UUID;

/**
 * What a caller may do: the single answer every enforcement point asks for (contract C2). Derived
 * by {@link AccessPolicy} from the caller's {@code Authentication}; never persisted.
 *
 * @param canReadAllExperiments {@code api:admin}, {@code api:experiment:write-all} or the system
 *     context
 * @param canWriteAllExperiments {@code api:admin} or {@code api:experiment:write-all}; never the
 *     system context
 * @param readableExperimentIds the experiments the caller may read; empty when {@code
 *     canReadAllExperiments}
 * @param writableExperimentIds the experiments whose metadata the caller may write; a subset of
 *     {@code readableExperimentIds}, empty when {@code canWriteAllExperiments}
 * @param canAccessDocuments {@code api:documents:read}
 * @param isAdmin {@code api:admin}; also answers {@link #canCreateExperiment()} and {@link
 *     #canManageSensors()}
 */
public record Capabilities(
    boolean canReadAllExperiments,
    boolean canWriteAllExperiments,
    Set<UUID> readableExperimentIds,
    Set<UUID> writableExperimentIds,
    boolean canAccessDocuments,
    boolean isAdmin) {

  /** Unauthenticated or unrecognised caller: nothing at all (fail closed). */
  public static final Capabilities NONE =
      new Capabilities(false, false, Set.of(), Set.of(), false, false);

  /**
   * Background jobs bound by {@link SystemSecurityContext#runAsSystem}: all-experiment read and
   * nothing else, like the system pseudo-user before MON-196.
   */
  public static final Capabilities SYSTEM =
      new Capabilities(true, false, Set.of(), Set.of(), false, false);

  public Capabilities {
    readableExperimentIds = Set.copyOf(readableExperimentIds);
    writableExperimentIds = Set.copyOf(writableExperimentIds);
  }

  /** Creating an experiment is an admin function. */
  public boolean canCreateExperiment() {
    return isAdmin;
  }

  /** Writing sensors is an admin function; reads are filtered by row-level security instead. */
  public boolean canManageSensors() {
    return isAdmin;
  }

  /** {@code canReadAllExperiments || readableExperimentIds.contains(id)}; a null id is never readable. */
  public boolean canReadExperiment(UUID experimentId) {
    return experimentId != null
        && (canReadAllExperiments || readableExperimentIds.contains(experimentId));
  }

  /** {@code canWriteAllExperiments || writableExperimentIds.contains(id)}; a null id is never writable. */
  public boolean canWriteExperiment(UUID experimentId) {
    return experimentId != null
        && (canWriteAllExperiments || writableExperimentIds.contains(experimentId));
  }
}
