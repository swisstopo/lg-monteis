package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.ADMIN_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.DOCUMENTS_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_AUTHORITY;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * What a caller may do: the single place that holds the privilege rules (ADR-001, contract C2).
 * The filter chain, {@code /api/me} and the RLS session context all ask it instead of checking
 * authorities themselves.
 *
 * <p>It stores only normalized facts - the caller's {@link Grant}s and the experiment ids assigned
 * to it - and computes every answer from them on each call, so no rule result is stored twice.
 * {@link #of(Authentication)} builds it from a Spring {@code Authentication}; it is never
 * persisted.
 *
 * @param grants the caller's grants
 * @param assignedReadExperimentIds the experiments assigned to the caller for reading, as {@link
 *     MonteisPrincipal} carries them
 * @param assignedWriteExperimentIds the experiments assigned to the caller for writing, as {@link
 *     MonteisPrincipal} carries them: always a subset of the read ones
 */
public record Capabilities(
    Set<Grant> grants, Set<UUID> assignedReadExperimentIds, Set<UUID> assignedWriteExperimentIds) {

  /** Unauthenticated or unrecognised caller: nothing at all (fail closed). */
  public static final Capabilities NONE = new Capabilities(Set.of(), Set.of(), Set.of());

  /**
   * Background jobs bound by {@link SystemSecurityContext#runAsSystem}: all-experiment read and
   * nothing else, like the system pseudo-user before MON-196.
   */
  public static final Capabilities SYSTEM =
      new Capabilities(Set.of(Grant.SYSTEM_READ_ALL), Set.of(), Set.of());

  private static final Logger log = LoggerFactory.getLogger(Capabilities.class);

  // SYSTEM_READ_ALL is deliberately absent: no authority, and so no request token, can grant it
  private static final Map<String, Grant> GRANT_BY_AUTHORITY =
      Map.of(
          EXPERIMENT_READ_AUTHORITY, Grant.EXPERIMENT_READ,
          EXPERIMENT_WRITE_AUTHORITY, Grant.EXPERIMENT_WRITE,
          EXPERIMENT_WRITE_ALL_AUTHORITY, Grant.EXPERIMENT_WRITE_ALL,
          DOCUMENTS_READ_AUTHORITY, Grant.DOCUMENTS_READ,
          ADMIN_AUTHORITY, Grant.ADMIN);

  public Capabilities {
    grants = Set.copyOf(grants);
    assignedReadExperimentIds = Set.copyOf(assignedReadExperimentIds);
    assignedWriteExperimentIds = Set.copyOf(assignedWriteExperimentIds);
  }

  /**
   * The capabilities of {@code authentication}: its {@code api:*} authorities become {@link
   * Grant}s, its principal's experiment ids are passed on. Returns {@link #SYSTEM} for the
   * authentication {@link SystemSecurityContext#runAsSystem} binds, and {@link #NONE} for {@code
   * null}, an unauthenticated token, a principal that is not a {@link MonteisPrincipal}, or when
   * reading the authentication fails for any reason.
   *
   * <p>It only reads the {@code Authentication}: no I/O, no cache and no mutable state, so every
   * replica decides identically for the same token (NFR3.2, NFR6.2). It never throws (NFR2.2).
   */
  public static Capabilities of(Authentication authentication) {
    try {
      return translate(authentication);
    } catch (RuntimeException e) {
      // fail closed; the exception type is enough for diagnosis and carries no token content
      log.warn(
          "Capability derivation failed ({}); denying all capabilities",
          e.getClass().getSimpleName());
      return NONE;
    }
  }

  private static Capabilities translate(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return NONE;
    }
    if (SystemSecurityContext.isSystemAuthentication(authentication)) {
      return SYSTEM;
    }
    if (!(authentication.getPrincipal() instanceof MonteisPrincipal principal)) {
      return NONE;
    }
    return new Capabilities(
        grantsOf(authentication.getAuthorities()),
        Set.copyOf(principal.getReadExperimentIds()),
        Set.copyOf(principal.getWriteExperimentIds()));
  }

  // authorities unknown to this app map to no grant
  private static Set<Grant> grantsOf(Collection<? extends GrantedAuthority> authorities) {
    return authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .filter(Objects::nonNull)
        .map(GRANT_BY_AUTHORITY::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }

  public boolean isAdmin() {
    return grants.contains(Grant.ADMIN);
  }

  /** Admins and global editors; never the system context, which reads but never writes. */
  public boolean canWriteAllExperiments() {
    return isAdmin() || grants.contains(Grant.EXPERIMENT_WRITE_ALL);
  }

  /** Whoever may write every experiment, plus the system context. */
  public boolean canReadAllExperiments() {
    return canWriteAllExperiments() || grants.contains(Grant.SYSTEM_READ_ALL);
  }

  /** Creating an experiment is an admin function. */
  public boolean canCreateExperiment() {
    return isAdmin();
  }

  /** Writing sensors is an admin function; reads are filtered by row-level security instead. */
  public boolean canManageSensors() {
    return isAdmin();
  }

  public boolean canAccessDocuments() {
    return grants.contains(Grant.DOCUMENTS_READ);
  }

  /** The experiments the caller may read one by one; empty when {@link #canReadAllExperiments()}. */
  public Set<UUID> readableExperimentIds() {
    return canReadAllExperiments() ? Set.of() : assignedReadExperimentIds;
  }

  /**
   * The experiments whose metadata the caller may write one by one: the assigned write ids, and
   * only with {@link Grant#EXPERIMENT_WRITE}. Empty when {@link #canWriteAllExperiments()}.
   */
  public Set<UUID> writableExperimentIds() {
    return canWriteAllExperiments() || !grants.contains(Grant.EXPERIMENT_WRITE)
        ? Set.of()
        : assignedWriteExperimentIds;
  }

  /** A null id is never readable. */
  public boolean canReadExperiment(UUID experimentId) {
    return experimentId != null
        && (canReadAllExperiments() || readableExperimentIds().contains(experimentId));
  }

  /** A null id is never writable. */
  public boolean canWriteExperiment(UUID experimentId) {
    return experimentId != null
        && (canWriteAllExperiments() || writableExperimentIds().contains(experimentId));
  }
}
