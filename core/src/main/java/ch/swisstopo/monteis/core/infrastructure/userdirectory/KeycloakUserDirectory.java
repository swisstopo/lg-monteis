package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAccessDeniedException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakClient;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakGroup;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUnavailableException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUser;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.AccessDenied;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.KeycloakUnavailable;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.Known;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.Pis.NoWriteGroup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The PIs of an experiment are the enabled members of the groups whose {@code
 * write_experiment_ids} attribute holds the experiment id (see the realm in {@code
 * docker/keycloak/realm}). We search by that attribute, never by the group name.
 *
 * <p>Keycloak answers with the requesting user's permissions, so the cache is per user: what one
 * user was allowed to read must never be served to another. Only complete answers are cached, a
 * failed lookup is asked again next time.
 */
@Component
class KeycloakUserDirectory implements UserDirectory {

  static final String WRITE_EXPERIMENT_IDS = "write_experiment_ids";

  private record CacheKey(UUID requestingUserId, UUID experimentId) {}

  private final KeycloakClient keycloak;
  private final CurrentUserProvider currentUser;
  private final Cache<CacheKey, Pis> cache;

  KeycloakUserDirectory(
      KeycloakClient keycloak,
      CurrentUserProvider currentUser,
      UserDirectoryProperties properties) {
    this.keycloak = keycloak;
    this.currentUser = currentUser;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.cacheTtl())
            .maximumSize(properties.cacheMaxSize())
            .build();
  }

  @Override
  public Pis pisForReading(UUID experimentId) {
    Optional<CacheKey> key = keyForRequestingUser(experimentId);
    if (key.isEmpty()) {
      return noRequestingUser();
    }
    return cachedPisOf(key.get()).orElseGet(() -> freshPisOf(key.get()));
  }

  @Override
  public Pis pisForWriting(UUID experimentId) {
    Optional<CacheKey> key = keyForRequestingUser(experimentId);
    if (key.isEmpty()) {
      return noRequestingUser();
    }
    return freshPisOf(key.get());
  }

  private Optional<CacheKey> keyForRequestingUser(UUID experimentId) {
    return currentUser.currentSubject().map(userId -> new CacheKey(userId, experimentId));
  }

  private static Pis noRequestingUser() {
    return new KeycloakUnavailable("no requesting user to ask Keycloak with");
  }

  private Pis freshPisOf(CacheKey key) {
    Pis pis = fetchPisFromKeycloak(key.experimentId());
    cacheIfComplete(key, pis);
    return pis;
  }

  private Optional<Pis> cachedPisOf(CacheKey key) {
    return Optional.ofNullable(cache.getIfPresent(key));
  }

  private void cacheIfComplete(CacheKey key, Pis pis) {
    if (pis instanceof Known || pis instanceof NoWriteGroup) {
      cache.put(key, pis);
    }
  }

  private Pis fetchPisFromKeycloak(UUID experimentId) {
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
        .map(DirectoryUser::from)
        .distinct()
        .sorted(DirectoryUser.BY_NAME)
        .toList();
  }
}
