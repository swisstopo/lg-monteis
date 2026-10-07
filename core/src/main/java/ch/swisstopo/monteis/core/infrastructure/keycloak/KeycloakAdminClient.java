package ch.swisstopo.monteis.core.infrastructure.keycloak;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The Keycloak admin API of the realm, asked with the caller's token. Knows Keycloak's paths and
 * representations, nothing about MONTEIS.
 */
public class KeycloakAdminClient {

  static final int MEMBERS_PAGE_SIZE = 100;

  private static final ParameterizedTypeReference<List<KeycloakGroup>> GROUPS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<KeycloakUser>> USERS =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;

  KeycloakAdminClient(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * Every group, at any depth, whose attribute {@code name} contains {@code value}.
   *
   * @throws KeycloakAccessDeniedException if the caller may not search groups
   * @throws KeycloakUnavailableException if Keycloak cannot be asked
   */
  public List<KeycloakGroup> findGroupsByAttribute(String name, String value) {
    List<KeycloakGroup> roots =
        call(
            () ->
                restClient
                    .get()
                    .uri(
                        uri ->
                            uri.path("/groups")
                                .queryParam("q", name + ":" + value)
                                .queryParam("briefRepresentation", false)
                                .build())
                    .retrieve()
                    .body(GROUPS));
    // the search answers with the top level groups and the path down to each hit, so the hit
    // itself can be any level deep
    return roots.stream()
        .flatMap(KeycloakGroup::withDescendants)
        .filter(group -> group.hasAttributeValue(name, value))
        .toList();
  }

  /**
   * All members of the group, disabled ones included.
   *
   * @throws KeycloakAccessDeniedException if the caller may not view the members
   * @throws KeycloakUnavailableException if Keycloak cannot be asked
   */
  public List<KeycloakUser> groupMembers(String groupId) {
    List<KeycloakUser> members = new ArrayList<>();
    List<KeycloakUser> page;
    do {
      page = membersPage(groupId, members.size());
      members.addAll(page);
    } while (page.size() == MEMBERS_PAGE_SIZE);
    return members;
  }

  private List<KeycloakUser> membersPage(String groupId, int first) {
    return call(
        () ->
            restClient
                .get()
                .uri(
                    uri ->
                        uri.path("/groups/{id}/members")
                            .queryParam("first", first)
                            .queryParam("max", MEMBERS_PAGE_SIZE)
                            // the brief representation leaves out first and last name
                            .queryParam("briefRepresentation", false)
                            .build(groupId))
                .retrieve()
                .body(USERS));
  }

  private static <T> List<T> call(Supplier<List<T>> request) {
    try {
      List<T> body = request.get();
      return body == null ? List.of() : body;
    } catch (HttpClientErrorException.Forbidden e) {
      throw new KeycloakAccessDeniedException("Keycloak admin API denied the request", e);
    } catch (RestClientException e) {
      throw new KeycloakUnavailableException("Keycloak admin API failed", e);
    }
  }
}
