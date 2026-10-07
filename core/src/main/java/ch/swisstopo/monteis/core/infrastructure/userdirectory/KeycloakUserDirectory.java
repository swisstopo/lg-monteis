package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAccessDeniedException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAdminClient;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakGroup;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUnavailableException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUser;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

  private final KeycloakAdminClient keycloak;
  private final CurrentUserProvider currentUser;
  private final Cache<CacheKey, PrincipalInvestigators> cache;

  KeycloakUserDirectory(
      KeycloakAdminClient keycloak,
      CurrentUserProvider currentUser,
      Duration cacheTtl,
      long cacheMaxSize) {
    this.keycloak = keycloak;
    this.currentUser = currentUser;
    this.cache = Caffeine.newBuilder().expireAfterWrite(cacheTtl).maximumSize(cacheMaxSize).build();
  }

  @Override
  public PrincipalInvestigators principalInvestigatorsOf(UUID experimentId) {
    return cacheKey(experimentId)
        .map(cache::getIfPresent)
        .orElseGet(() -> currentPrincipalInvestigatorsOf(experimentId));
  }

  @Override
  public PrincipalInvestigators currentPrincipalInvestigatorsOf(UUID experimentId) {
    Optional<CacheKey> key = cacheKey(experimentId);
    if (key.isEmpty()) {
      return new PrincipalInvestigators.KeycloakUnavailable("no caller to ask Keycloak with");
    }
    PrincipalInvestigators lookup = load(experimentId);
    if (lookup instanceof PrincipalInvestigators.Known
        || lookup instanceof PrincipalInvestigators.NoWriteGroup) {
      cache.put(key.get(), lookup);
    }
    return lookup;
  }

  private Optional<CacheKey> cacheKey(UUID experimentId) {
    return currentUser.currentSubject().map(caller -> new CacheKey(caller, experimentId));
  }

  private PrincipalInvestigators load(UUID experimentId) {
    try {
      List<KeycloakGroup> writeGroups =
          keycloak.findGroupsByAttribute(WRITE_EXPERIMENT_IDS, experimentId.toString());
      if (writeGroups.isEmpty()) {
        return new PrincipalInvestigators.NoWriteGroup();
      }
      return new PrincipalInvestigators.Known(enabledMembers(writeGroups));
    } catch (KeycloakAccessDeniedException e) {
      return new PrincipalInvestigators.AccessDenied(e.getMessage());
    } catch (KeycloakUnavailableException e) {
      return new PrincipalInvestigators.KeycloakUnavailable(e.getMessage());
    }
  }

  private List<DirectoryUser> enabledMembers(List<KeycloakGroup> groups) {
    return groups.stream()
        .flatMap(group -> keycloak.groupMembers(group.id()).stream())
        .filter(KeycloakUser::enabled)
        .collect(Collectors.toMap(KeycloakUser::id, Function.identity(), (first, _) -> first))
        .values()
        .stream()
        .map(KeycloakUserDirectory::toDirectoryUser)
        .sorted(DirectoryUser.BY_NAME)
        .toList();
  }

  private static DirectoryUser toDirectoryUser(KeycloakUser user) {
    return new DirectoryUser(user.id(), user.firstName(), user.lastName(), user.email());
  }
}
