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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class KeycloakUserDirectory implements UserDirectory {

  static final String READ_EXPERIMENT_IDS = "read_experiment_ids";
  static final String WRITE_EXPERIMENT_IDS = "write_experiment_ids";

  private static final String NO_REQUESTING_USER = "no requesting user to ask Keycloak with";
  private static final Logger log = LoggerFactory.getLogger(KeycloakUserDirectory.class);

  private record CacheKey(UUID requestingUserId, UUID experimentId) {}

  private final KeycloakClient keycloak;
  private final CurrentUserProvider currentUser;
  private final Cache<CacheKey, Pis> cache;
  private final Cache<CacheKey, ExperimentUsers> experimentUsersCache;

  KeycloakUserDirectory(
      KeycloakClient keycloak,
      CurrentUserProvider currentUser,
      UserDirectoryProperties properties) {
    this.keycloak = keycloak;
    this.currentUser = currentUser;
    this.cache = newCache(properties);
    this.experimentUsersCache = newCache(properties);
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
    return new KeycloakUnavailable(NO_REQUESTING_USER);
  }

  private static Map<UUID, Pis> eachWith(Collection<UUID> experimentIds, Pis pis) {
    Map<UUID, Pis> pisByExperiment = new LinkedHashMap<>();
    experimentIds.forEach(id -> pisByExperiment.put(id, pis));
    return pisByExperiment;
  }

  private Map<UUID, Pis> loadPisUntilKeycloakFails(List<CacheKey> keys) {
    Map<UUID, Pis> pisByExperiment = new LinkedHashMap<>();
    KeycloakUnavailable outage = null;
    for (CacheKey key : keys) {
      Pis pis = cache.getIfPresent(key);
      if (pis == null) {
        pis = outage != null ? outage : loadPis(key);
      }
      if (pis instanceof KeycloakUnavailable unavailable) {
        outage = unavailable;
      }
      pisByExperiment.put(key.experimentId(), pis);
    }
    return pisByExperiment;
  }

  private Pis loadPis(CacheKey key) {
    Pis pis = fetchPisFromKeycloak(key.experimentId());
    cacheKeycloakAnswer(key, pis);
    return pis;
  }

  private void cacheKeycloakAnswer(CacheKey key, Pis pis) {
    if (pis instanceof Known) {
      cache.put(key, pis);
    }
  }

  private Pis fetchPisFromKeycloak(UUID experimentId) {
    try {
      return new Known(enabledMembersOf(findWriteGroupsOf(experimentId)));
    } catch (UserDirectoryDeniedException e) {
      return new AccessDenied(e.getMessage());
    } catch (UserDirectoryUnavailableException e) {
      log.warn("Keycloak is unavailable, not asking it again in this call: {}", e.getMessage());
      return new KeycloakUnavailable(e.getMessage());
    }
  }

  private List<KeycloakGroup> findWriteGroupsOf(UUID experimentId) {
    return findGroupsWith(WRITE_EXPERIMENT_IDS, experimentId);
  }

  private List<KeycloakGroup> findGroupsWith(String attributeName, UUID experimentId) {
    return askKeycloak(
        () -> keycloak.findGroupsByAttribute(attributeName, experimentId.toString()));
  }

  private List<DirectoryUser> enabledMembersOf(List<KeycloakGroup> groups) {
    return distinctSortedByName(
        groups.stream()
            .flatMap(group -> askKeycloak(() -> keycloak.groupMembers(group.id())).stream())
            .filter(KeycloakUser::enabled)
            .map(DirectoryUser::from));
  }

  @Override
  public ExperimentUsers findExperimentUsersOf(UUID experimentId) {
    CacheKey key =
        keyForRequestingUser(experimentId)
            .orElseThrow(() -> new UserDirectoryUnavailableException(NO_REQUESTING_USER));
    // loader throws on failure => caffeine caches answers only
    return experimentUsersCache.get(key, k -> fetchExperimentUsersFromKeycloak(k.experimentId()));
  }

  private ExperimentUsers fetchExperimentUsersFromKeycloak(UUID experimentId) {
    List<DirectoryUser> pis = enabledMembersOf(findWriteGroupsOf(experimentId));
    List<DirectoryUser> readers =
        enabledMembersOf(findGroupsWith(READ_EXPERIMENT_IDS, experimentId));
    return new ExperimentUsers(
        distinctSortedByName(Stream.concat(pis.stream(), readers.stream())), pis);
  }

  private static <T> T askKeycloak(Supplier<T> request) {
    try {
      return request.get();
    } catch (KeycloakAccessDeniedException e) {
      throw new UserDirectoryDeniedException(e.getMessage());
    } catch (KeycloakUnavailableException e) {
      throw new UserDirectoryUnavailableException(e.getMessage());
    }
  }

  private static List<DirectoryUser> distinctSortedByName(Stream<DirectoryUser> users) {
    return users.distinct().sorted(DirectoryUser.BY_NAME).toList();
  }

  private static <V> Cache<CacheKey, V> newCache(UserDirectoryProperties properties) {
    return Caffeine.newBuilder()
        .expireAfterWrite(properties.cacheTtl())
        .maximumSize(properties.cacheMaxSize())
        .build();
  }
}
