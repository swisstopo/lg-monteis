package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAccessDeniedException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakClient;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakGroup;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUnavailableException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUser;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.AccessDenied;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.KeycloakUnavailable;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.Known;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.PrincipalInvestigators.NoWriteGroup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The PIs of an experiment are the enabled members of the groups whose {@code
 * write_experiment_ids} attribute holds the experiment id (see the realm in {@code
 * docker/keycloak/realm}). We search by that attribute, never by the group name.
 *
 * <p>Keycloak answers with the caller's permissions, so the cache is per caller: what one user was
 * allowed to read must never be served to another. Only complete answers are cached, a failed
 * lookup is asked again next time.
 */
class KeycloakUserDirectory implements UserDirectory {

  static final String WRITE_EXPERIMENT_IDS = "write_experiment_ids";

  private record CacheKey(UUID caller, UUID experimentId) {}

  private final KeycloakClient keycloak;
  private final CurrentUserProvider currentUser;
  private final Cache<CacheKey, PrincipalInvestigators> cache;

  KeycloakUserDirectory(
      KeycloakClient keycloak,
      CurrentUserProvider currentUser,
      Duration cacheTtl,
      long cacheMaxSize) {
    this.keycloak = keycloak;
    this.currentUser = currentUser;
    this.cache = Caffeine.newBuilder().expireAfterWrite(cacheTtl).maximumSize(cacheMaxSize).build();
  }

  @Override
  public PrincipalInvestigators principalInvestigatorsOf(UUID experimentId) {
    Optional<CacheKey> key = keyForCurrentCaller(experimentId);
    if (key.isEmpty()) {
      return noCaller();
    }
    PrincipalInvestigators cached = cache.getIfPresent(key.get());
    if (cached != null) {
      return cached;
    }
    return askKeycloakAndCache(key.get());
  }

  @Override
  public PrincipalInvestigators currentPrincipalInvestigatorsOf(UUID experimentId) {
    Optional<CacheKey> key = keyForCurrentCaller(experimentId);
    if (key.isEmpty()) {
      return noCaller();
    }
    return askKeycloakAndCache(key.get());
  }

  private Optional<CacheKey> keyForCurrentCaller(UUID experimentId) {
    return currentUser.currentSubject().map(caller -> new CacheKey(caller, experimentId));
  }

  private static PrincipalInvestigators noCaller() {
    return new KeycloakUnavailable("no caller to ask Keycloak with");
  }

  private PrincipalInvestigators askKeycloakAndCache(CacheKey key) {
    PrincipalInvestigators answer = askKeycloak(key.experimentId());
    if (isComplete(answer)) {
      cache.put(key, answer);
    }
    return answer;
  }

  private static boolean isComplete(PrincipalInvestigators answer) {
    return answer instanceof Known || answer instanceof NoWriteGroup;
  }

  private PrincipalInvestigators askKeycloak(UUID experimentId) {
    try {
      List<KeycloakGroup> writeGroups = writeGroupsOf(experimentId);
      if (writeGroups.isEmpty()) {
        return new NoWriteGroup();
      }
      return new Known(enabledMembersOf(writeGroups));
    } catch (KeycloakAccessDeniedException e) {
      return new AccessDenied(e.getMessage());
    } catch (KeycloakUnavailableException e) {
      return new KeycloakUnavailable(e.getMessage());
    }
  }

  private List<KeycloakGroup> writeGroupsOf(UUID experimentId) {
    return keycloak.findGroupsByAttribute(WRITE_EXPERIMENT_IDS, experimentId.toString());
  }

  /** Someone in two write groups is listed once. */
  private List<DirectoryUser> enabledMembersOf(List<KeycloakGroup> groups) {
    return groups.stream()
        .flatMap(group -> keycloak.groupMembers(group.id()).stream())
        .filter(KeycloakUser::enabled)
        .map(KeycloakUserDirectory::toDirectoryUser)
        .distinct()
        .sorted(DirectoryUser.BY_NAME)
        .toList();
  }

  private static DirectoryUser toDirectoryUser(KeycloakUser user) {
    return new DirectoryUser(user.id(), user.firstName(), user.lastName(), user.email());
  }
}
