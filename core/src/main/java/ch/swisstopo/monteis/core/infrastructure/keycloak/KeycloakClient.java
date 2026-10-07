package ch.swisstopo.monteis.core.infrastructure.keycloak;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Keycloak's admin REST API for the realm, asked with the caller's token. Knows Keycloak's paths
 * and representations, nothing about MONTEIS.
 */
public class KeycloakClient {

  /** Keycloak's members endpoint returns 100 users unless asked otherwise, -1 lifts the limit. */
  static final int ALL_MEMBERS = -1;

  private static final ParameterizedTypeReference<List<KeycloakGroup>> GROUPS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<KeycloakUser>> USERS =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;

  KeycloakClient(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * Every group, at any depth, whose attribute {@code attributeName} contains {@code
   * attributeValue}.
   *
   * @throws KeycloakAccessDeniedException if the caller may not search groups
   * @throws KeycloakUnavailableException if Keycloak cannot be asked
   */
  public List<KeycloakGroup> findGroupsByAttribute(String attributeName, String attributeValue) {
    // the search answers with the top level groups and the path down to each hit, so the hit
    // itself can be any level deep
    return searchGroups(attributeName + ":" + attributeValue).stream()
        .flatMap(KeycloakGroup::selfAndAllSubgroups)
        .filter(group -> group.hasAttributeValue(attributeName, attributeValue))
        .toList();
  }

  /**
   * All members of the group, disabled ones included.
   *
   * @throws KeycloakAccessDeniedException if the caller may not view the members
   * @throws KeycloakUnavailableException if Keycloak cannot be asked
   */
  public List<KeycloakUser> groupMembers(String groupId) {
    return getList(
        uri ->
            uri.path("/groups/{id}/members")
                .queryParam("max", ALL_MEMBERS)
                // the brief representation leaves out first and last name
                .queryParam("briefRepresentation", false)
                .build(groupId),
        USERS);
  }

  private List<KeycloakGroup> searchGroups(String query) {
    return getList(
        uri ->
            uri.path("/groups")
                .queryParam("q", query)
                .queryParam("briefRepresentation", false)
                .build(),
        GROUPS);
  }

  private <T> List<T> getList(
      Function<UriBuilder, URI> uri, ParameterizedTypeReference<List<T>> type) {
    try {
      List<T> body = restClient.get().uri(uri).retrieve().body(type);
      return body == null ? List.of() : body;
    } catch (HttpClientErrorException.Forbidden e) {
      throw new KeycloakAccessDeniedException("Keycloak admin API denied the request", e);
    } catch (RestClientException e) {
      throw new KeycloakUnavailableException("Keycloak admin API failed", e);
    }
  }
}
