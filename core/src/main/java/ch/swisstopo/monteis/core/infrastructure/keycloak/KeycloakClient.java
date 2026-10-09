package ch.swisstopo.monteis.core.infrastructure.keycloak;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

public class KeycloakClient {

  static final int ALL_MEMBERS = -1;

  private static final ParameterizedTypeReference<List<KeycloakGroup>> GROUPS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<KeycloakUser>> USERS =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;

  KeycloakClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public List<KeycloakGroup> findGroupsByAttribute(String attributeName, String attributeValue) {
    // the search answers with the top level groups and the path down to each hit, so the hit
    // itself can be any level deep
    return searchGroups(attributeName + ":" + attributeValue).stream()
        .flatMap(KeycloakGroup::selfAndAllSubgroups)
        .filter(group -> group.hasAttributeValue(attributeName, attributeValue))
        .toList();
  }

  public List<KeycloakUser> groupMembers(String groupId) {
    return getList(
        uri ->
            // the brief representation already has names, email and enabled, the full one only
            // adds what we don't read
            uri.path("/groups/{id}/members").queryParam("max", ALL_MEMBERS).build(groupId),
        USERS);
  }

  private List<KeycloakGroup> searchGroups(String query) {
    return getList(
        uri ->
            uri.path("/groups")
                .queryParam("q", query)
                // the brief representation leaves out the attributes the hits are filtered on
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
