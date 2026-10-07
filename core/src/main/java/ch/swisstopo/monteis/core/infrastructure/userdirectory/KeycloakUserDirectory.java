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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(KeycloakUserDirectory.class);

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
  public Map<UUID, Pis> pisForReading(Collection<UUID> experimentIds) {
    Optional<UUID> requestingUserId = currentUser.currentSubject();
    if (requestingUserId.isEmpty()) {
      return eachWith(experimentIds, noRequestingUser());
    }
    List<CacheKey> keys =
        experimentIds.stream().map(id -> new CacheKey(requestingUserId.get(), id)).toList();
    return loadPisUntilKeycloakFails(keys);
  }

  @Override
  public Pis pisForWriting(UUID experimentId) {
    Optional<CacheKey> key = keyForRequestingUser(experimentId);
    return key.map(this::loadPis).orElseGet(KeycloakUserDirectory::noRequestingUser);
  }

  private Optional<CacheKey> keyForRequestingUser(UUID experimentId) {
    return currentUser.currentSubject().map(userId -> new CacheKey(userId, experimentId));
  }

  private static Pis noRequestingUser() {
    return new KeycloakUnavailable("no requesting user to ask Keycloak with");
  }

  private static Map<UUID, Pis> eachWith(Collection<UUID> experimentIds, Pis pis) {
    Map<UUID, Pis> pisByExperiment = new LinkedHashMap<>();
    experimentIds.forEach(id -> pisByExperiment.put(id, pis));
    return pisByExperiment;
  }

  /**
   * Once Keycloak is unavailable it is not asked again, the remaining experiments get the same
   * answer instead of waiting for another timeout each. Cached answers are still used.
   */
  private Map<UUID, Pis> loadPisUntilKeycloakFails(List<CacheKey> keys) {
    Map<UUID, Pis> pisByExperiment = new LinkedHashMap<>();
    Optional<Pis> earlierFailure = Optional.empty();
    for (CacheKey key : keys) {
      Pis pis = cachedOrLoadedPisOf(key, earlierFailure);
      if (pis instanceof KeycloakUnavailable) {
        earlierFailure = Optional.of(pis);
      }
      pisByExperiment.put(key.experimentId(), pis);
    }
    return pisByExperiment;
  }

  private Pis cachedOrLoadedPisOf(CacheKey key, Optional<Pis> earlierFailure) {
    Optional<Pis> cached = cachedPisOf(key);
    if (cached.isPresent()) {
      return cached.get();
    }
    if (earlierFailure.isPresent()) {
      return earlierFailure.get();
    }
    return loadPis(key);
  }

  private Optional<Pis> cachedPisOf(CacheKey key) {
    return Optional.ofNullable(cache.getIfPresent(key));
  }

  private Pis loadPis(CacheKey key) {
    Pis pis = fetchPisFromKeycloak(key.experimentId());
    cacheKeycloakAnswer(key, pis);
    return pis;
  }

  /** What Keycloak answered, failures to ask it are asked again next time. */
  private void cacheKeycloakAnswer(CacheKey key, Pis pis) {
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
      log.warn("Keycloak is unavailable, not asking it again in this call: {}", e.getMessage());
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
