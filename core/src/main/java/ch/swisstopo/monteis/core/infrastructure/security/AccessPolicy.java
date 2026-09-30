package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.ADMIN_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.DOCUMENTS_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_READ_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_ALL_AUTHORITY;
import static ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities.EXPERIMENT_WRITE_AUTHORITY;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * The single source of privilege rules in {@code core} (ADR-001, contract C2). The filter chain,
 * {@code /api/me} and the RLS session context all ask it for the caller's {@link Capabilities}
 * instead of checking authorities themselves.
 *
 * <p>Derivation only reads the {@link Authentication}: no I/O, no cache and no mutable state, so
 * every replica decides identically for the same token (NFR3.2, NFR6.2). It never throws; any
 * failure yields {@link Capabilities#NONE} (NFR2.2).
 */
public final class AccessPolicy {

  private static final Logger log = LoggerFactory.getLogger(AccessPolicy.class);

  private AccessPolicy() {}

  /** The capabilities of background jobs: all-experiment read, no admin functions. */
  public static Capabilities systemCapabilities() {
    return Capabilities.SYSTEM;
  }

  /**
   * The capabilities of {@code authentication}. Returns {@code NONE} for {@code null}, an
   * unauthenticated token, a principal that is not a {@link MonteisPrincipal}, or when derivation
   * fails for any reason.
   */
  public static Capabilities capabilitiesOf(Authentication authentication) {
    try {
      return derive(authentication);
    } catch (RuntimeException e) {
      // fail closed; the exception type is enough for diagnosis and carries no token content
      log.warn(
          "Capability derivation failed ({}); denying all capabilities",
          e.getClass().getSimpleName());
      return Capabilities.NONE;
    }
  }

  /** Whether a token's {@code read_experiment_ids} claim may be trusted (BR4.2). */
  static boolean grantsScopedExperimentRead(Collection<? extends GrantedAuthority> authorities) {
    return hasAuthority(authorities, EXPERIMENT_READ_AUTHORITY);
  }

  /** Whether a token's {@code write_experiment_ids} claim may be trusted (BR4.2). */
  static boolean grantsScopedExperimentWrite(Collection<? extends GrantedAuthority> authorities) {
    return hasAuthority(authorities, EXPERIMENT_WRITE_AUTHORITY);
  }

  private static Capabilities derive(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return Capabilities.NONE;
    }
    if (SystemSecurityContext.isSystemAuthentication(authentication)) {
      return systemCapabilities();
    }
    if (!(authentication.getPrincipal() instanceof MonteisPrincipal principal)) {
      return Capabilities.NONE;
    }

    Set<String> authorities = authorityNames(authentication.getAuthorities());
    boolean admin = authorities.contains(ADMIN_AUTHORITY);
    boolean allExperiments = admin || authorities.contains(EXPERIMENT_WRITE_ALL_AUTHORITY);

    Set<UUID> readable = allExperiments ? Set.of() : Set.copyOf(principal.getReadExperimentIds());
    Set<UUID> editable =
        allExperiments || !authorities.contains(EXPERIMENT_WRITE_AUTHORITY)
            ? Set.of()
            // MonteisPrincipal keeps its write ids a subset of its read ids
            : Set.copyOf(principal.getWriteExperimentIds());

    return new Capabilities(
        allExperiments,
        allExperiments,
        readable,
        editable,
        admin,
        admin,
        authorities.contains(DOCUMENTS_READ_AUTHORITY),
        admin,
        admin);
  }

  private static Set<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
    return authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static boolean hasAuthority(
      Collection<? extends GrantedAuthority> authorities, String authority) {
    return authorities.stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }
}
