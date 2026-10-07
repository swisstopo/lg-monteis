package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads experiment PIs through the Keycloak admin API. The groups granting write access carry
 * the experiment id in their {@code write_experiment_ids} attribute (see the realm in {@code
 * docker/keycloak/realm}), that attribute is what we search for, never the group name.
 *
 * <p>Every call carries the caller's token, so the cache is per caller: what one user was allowed
 * to read must never be served to another.
 */
class KeycloakUserDirectory implements UserDirectory {

  static final String WRITE_EXPERIMENT_IDS = "write_experiment_ids";
  static final int MEMBERS_PAGE_SIZE = 100;

  private static final ParameterizedTypeReference<List<KeycloakGroup>> GROUPS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<KeycloakUser>> USERS =
      new ParameterizedTypeReference<>() {};

  private record CacheKey(UUID caller, UUID experimentId) {}

  private final RestClient keycloakAdmin;
  private final Supplier<Optional<UUID>> currentCaller;
  private final Cache<CacheKey, List<DirectoryUser>> principalInvestigators;

  KeycloakUserDirectory(
      RestClient keycloakAdmin, Supplier<Optional<UUID>> currentCaller, Duration cacheTtl) {
    this.keycloakAdmin = keycloakAdmin;
    this.currentCaller = currentCaller;
    this.principalInvestigators = Caffeine.newBuilder().expireAfterWrite(cacheTtl).build();
  }

  @Override
  public List<DirectoryUser> principalInvestigatorsOf(UUID experimentId) {
    UUID caller =
        currentCaller
            .get()
            .orElseThrow(
                () ->
                    new UserDirectoryUnavailableException(
                        "No caller to ask Keycloak for the PIs of experiment " + experimentId,
                        null));
    return principalInvestigators.get(
        new CacheKey(caller, experimentId), key -> loadPrincipalInvestigators(key.experimentId()));
  }

  private List<DirectoryUser> loadPrincipalInvestigators(UUID experimentId) {
    try {
      Map<UUID, DirectoryUser> members = new LinkedHashMap<>();
      for (KeycloakGroup group : writeGroupsOf(experimentId)) {
        for (KeycloakUser user : membersOf(group)) {
          if (user.enabled()) {
            members.putIfAbsent(user.id(), user.toDirectoryUser());
          }
        }
      }
      return members.values().stream().sorted(DirectoryUser.BY_NAME).toList();
    } catch (HttpClientErrorException.Forbidden e) {
      throw new UserDirectoryAccessDeniedException(
          "Keycloak denied reading the PIs of experiment " + experimentId, e);
    } catch (RestClientException e) {
      throw new UserDirectoryUnavailableException(
          "Keycloak admin API failed reading the PIs of experiment " + experimentId, e);
    }
  }

  private List<KeycloakGroup> writeGroupsOf(UUID experimentId) {
    String id = experimentId.toString();
    List<KeycloakGroup> matches =
        keycloakAdmin
            .get()
            .uri(
                uri ->
                    uri.path("/groups")
                        .queryParam("q", WRITE_EXPERIMENT_IDS + ":" + id)
                        .queryParam("briefRepresentation", false)
                        .build())
            .retrieve()
            .body(GROUPS);
    // the search answers with the top level groups and the path down to each hit, so the hit
    // itself can be any level deep
    return matches == null
        ? List.of()
        : matches.stream()
            .flatMap(KeycloakGroup::withDescendants)
            .filter(group -> group.hasAttributeValue(WRITE_EXPERIMENT_IDS, id))
            .toList();
  }

  private List<KeycloakUser> membersOf(KeycloakGroup group) {
    List<KeycloakUser> members = new ArrayList<>();
    List<KeycloakUser> page;
    do {
      int first = members.size();
      page =
          keycloakAdmin
              .get()
              .uri(
                  uri ->
                      uri.path("/groups/{id}/members")
                          .queryParam("first", first)
                          .queryParam("max", MEMBERS_PAGE_SIZE)
                          // the brief representation leaves out first and last name
                          .queryParam("briefRepresentation", false)
                          .build(group.id()))
              .retrieve()
              .body(USERS);
      if (page != null) {
        members.addAll(page);
      }
    } while (page != null && page.size() == MEMBERS_PAGE_SIZE);
    return members;
  }
}
