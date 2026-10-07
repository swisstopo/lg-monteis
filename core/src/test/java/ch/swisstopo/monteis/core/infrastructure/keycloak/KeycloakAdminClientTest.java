package ch.swisstopo.monteis.core.infrastructure.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class KeycloakAdminClientTest {

  private static final String ADMIN_URI = "http://keycloak/admin/realms/monteis";
  private static final String SEARCH_URI =
      ADMIN_URI + "/groups?q=write_experiment_ids:exp-1&briefRepresentation=false";

  private MockRestServiceServer keycloak;
  private KeycloakAdminClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(ADMIN_URI);
    keycloak = MockRestServiceServer.bindTo(builder).build();
    client = new KeycloakAdminClient(builder.build());
  }

  @Test
  void should_find_groups_at_any_depth_by_attribute() {
    keycloak
        .expect(once(), requestTo(SEARCH_URI))
        .andRespond(
            json(
                """
                [{"id":"top","path":"/Experiments","subGroups":[
                  {"id":"alpha","path":"/Experiments/Alpha","subGroups":[
                    {"id":"rw","path":"/Experiments/Alpha/read + write",
                     "attributes":{"write_experiment_ids":["exp-1"]}}]}]}]
                """));

    List<KeycloakGroup> groups = client.findGroupsByAttribute("write_experiment_ids", "exp-1");

    assertEquals(List.of("rw"), groups.stream().map(KeycloakGroup::id).toList());
  }

  @Test
  void should_answer_an_empty_body_with_no_groups() {
    keycloak.expect(once(), requestTo(SEARCH_URI)).andRespond(withSuccess());

    assertEquals(List.of(), client.findGroupsByAttribute("write_experiment_ids", "exp-1"));
  }

  @Test
  void should_page_through_the_members() {
    keycloak
        .expect(once(), requestTo(membersUri(0)))
        .andRespond(json(users(KeycloakAdminClient.MEMBERS_PAGE_SIZE)));
    keycloak.expect(once(), requestTo(membersUri(100))).andRespond(json(users(1)));

    assertEquals(KeycloakAdminClient.MEMBERS_PAGE_SIZE + 1, client.groupMembers("rw").size());
    keycloak.verify();
  }

  @Test
  void should_report_a_denial_as_access_denied() {
    keycloak.expect(once(), requestTo(SEARCH_URI)).andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThrows(
        KeycloakAccessDeniedException.class,
        () -> client.findGroupsByAttribute("write_experiment_ids", "exp-1"));
  }

  @Test
  void should_report_a_server_error_as_unavailable() {
    keycloak.expect(once(), requestTo(membersUri(0))).andRespond(withServerError());

    assertThrows(KeycloakUnavailableException.class, () -> client.groupMembers("rw"));
  }

  private static String membersUri(int first) {
    return ADMIN_URI + "/groups/rw/members?first=" + first + "&max=100&briefRepresentation=false";
  }

  private static String users(int count) {
    return IntStream.range(0, count)
        .mapToObj(_ -> "{\"id\":\"%s\",\"enabled\":true}".formatted(UUID.randomUUID()))
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static ResponseCreator json(String body) {
    return withSuccess(body, MediaType.APPLICATION_JSON);
  }
}
