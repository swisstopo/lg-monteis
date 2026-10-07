package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakUserDirectoryTest {

  private static final String ADMIN_URI = "http://keycloak/admin/realms/monteis";
  private static final UUID EXPERIMENT_ID = UUID.randomUUID();
  private static final UUID ALICE_ID = UUID.randomUUID();
  private static final UUID CALLER = UUID.randomUUID();
  private static final UUID OTHER_CALLER = UUID.randomUUID();

  private final AtomicReference<Optional<UUID>> caller = new AtomicReference<>(Optional.of(CALLER));
  private MockRestServiceServer keycloak;
  private KeycloakUserDirectory directory;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(ADMIN_URI);
    keycloak = MockRestServiceServer.bindTo(builder).build();
    directory = new KeycloakUserDirectory(builder.build(), caller::get, Duration.ofMinutes(1));
  }

  @Test
  void should_read_the_enabled_members_of_the_write_group() {
    expectPiLookup();

    List<DirectoryUser> pis = directory.principalInvestigatorsOf(EXPERIMENT_ID);

    assertEquals(
        List.of(new DirectoryUser(ALICE_ID, "Alice", "Example", "alice@example.test")), pis);
    keycloak.verify();
  }

  @Test
  void should_cache_per_caller_so_no_caller_sees_what_another_was_allowed_to_read() {
    expectPiLookup();
    expectPiLookup();

    directory.principalInvestigatorsOf(EXPERIMENT_ID);
    directory.principalInvestigatorsOf(EXPERIMENT_ID);
    caller.set(Optional.of(OTHER_CALLER));
    directory.principalInvestigatorsOf(EXPERIMENT_ID);

    keycloak.verify();
  }

  @Test
  void should_report_a_denial_apart_from_an_unavailable_keycloak() {
    keycloak
        .expect(once(), requestTo(groupSearchUri()))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThrows(
        UserDirectoryAccessDeniedException.class,
        () -> directory.principalInvestigatorsOf(EXPERIMENT_ID));
  }

  @Test
  void should_report_keycloak_unavailable_on_a_server_error() {
    keycloak.expect(once(), requestTo(groupSearchUri())).andRespond(withServerError());

    UserDirectoryUnavailableException e =
        assertThrows(
            UserDirectoryUnavailableException.class,
            () -> directory.principalInvestigatorsOf(EXPERIMENT_ID));
    assertEquals(UserDirectoryUnavailableException.class, e.getClass());
  }

  @Test
  void should_not_ask_keycloak_without_a_caller() {
    caller.set(Optional.empty());

    assertThrows(
        UserDirectoryUnavailableException.class,
        () -> directory.principalInvestigatorsOf(EXPERIMENT_ID));
    keycloak.verify();
  }

  private void expectPiLookup() {
    String groupId = UUID.randomUUID().toString();
    keycloak
        .expect(once(), requestTo(groupSearchUri()))
        .andRespond(
            withSuccess(
                """
                [{"id":"top","path":"/Experiments","subGroups":[
                  {"id":"%s","path":"/Experiments/Alpha/read + write",
                   "attributes":{"write_experiment_ids":["%s"]}}]}]
                """
                    .formatted(groupId, EXPERIMENT_ID),
                MediaType.APPLICATION_JSON));
    keycloak
        .expect(
            once(),
            requestTo(
                ADMIN_URI
                    + "/groups/"
                    + groupId
                    + "/members?first=0&max=100&briefRepresentation=false"))
        .andRespond(
            withSuccess(
                """
                [{"id":"%s","firstName":"Alice","lastName":"Example",
                  "email":"alice@example.test","enabled":true},
                 {"id":"%s","firstName":"Dan","lastName":"Disabled","enabled":false}]
                """
                    .formatted(ALICE_ID, UUID.randomUUID()),
                MediaType.APPLICATION_JSON));
  }

  private static String groupSearchUri() {
    return ADMIN_URI
        + "/groups?q=write_experiment_ids:"
        + EXPERIMENT_ID
        + "&briefRepresentation=false";
  }
}
