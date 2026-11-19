package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakConfig;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakProperties;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.itconfig.RealmRenderer;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// realm: docker/keycloak/realm, Alpha read + write = alice, Beta read = bob, Gamma read = alice
@Testcontainers
@SpringJUnitConfig(KeycloakUserDirectoryIT.Config.class)
class KeycloakUserDirectoryIT {

  private static final UUID ALICE = UUID.fromString("a11ce43c-8733-4a5e-a389-4760cbc21984");
  private static final UUID BOB = UUID.fromString("b0bb4079-a512-492e-bec7-ede4b410397c");
  private static final UUID ADMIN_USER = UUID.fromString("4efbee50-d322-44ed-aece-92dbf9b9d969");
  private static final UUID BASIS_USER = UUID.fromString("ba51c5e7-3a22-4906-b74e-3688e6d738e8");

  @Container static final KeycloakContainer keycloak = keycloakWithLocalRealm();

  @Autowired private UserDirectory directory;
  @MockitoBean private CurrentUserProvider currentUser;

  @DynamicPropertySource
  static void keycloakApi(DynamicPropertyRegistry registry) {
    registry.add(
        "monteis.keycloak.api-uri", () -> keycloak.getAuthServerUrl() + "/admin/realms/monteis");
  }

  @Test
  void should_find_the_members_and_the_pis_of_an_experiment() {
    // given
    signedInAs(ADMIN_USER, "admin-user");

    // when
    ExperimentMembers alpha = directory.findMembersOf(SeedData.EXPERIMENT_ALPHA);
    ExperimentMembers beta = directory.findMembersOf(SeedData.EXPERIMENT_BETA);
    ExperimentMembers gamma = directory.findMembersOf(SeedData.EXPERIMENT_GAMMA);

    // then
    assertThat(idsOf(alpha.all())).containsExactly(ALICE);
    assertThat(idsOf(alpha.pis())).containsExactly(ALICE);
    assertThat(idsOf(beta.all())).containsExactly(BOB);
    assertThat(idsOf(beta.pis())).isEmpty();
    assertThat(idsOf(gamma.all())).containsExactly(ALICE);
  }

  @Test
  void should_let_a_pi_find_the_members_with_their_contact_details() {
    // given
    signedInAs(ALICE, "alice");

    // when
    ExperimentMembers alpha = directory.findMembersOf(SeedData.EXPERIMENT_ALPHA);

    // then
    assertThat(alpha.all())
        .containsExactly(new DirectoryUser(ALICE, "Alice", "Example", "alice@example.test"));
  }

  @Test
  void should_be_denied_for_a_user_without_access_to_any_experiment() {
    // given
    signedInAs(BASIS_USER, "basis-user");

    // when / then
    assertThrows(
        UserDirectoryDeniedException.class,
        () -> directory.findMembersOf(SeedData.EXPERIMENT_ALPHA));
  }

  @Test
  void should_find_no_members_of_an_experiment_without_groups() {
    // given
    signedInAs(ADMIN_USER, "admin-user");

    // when
    ExperimentMembers users = directory.findMembersOf(UUID.randomUUID());

    // then
    assertThat(users).isEqualTo(new ExperimentMembers(List.of(), List.of()));
  }

  // seed users' passwords = their usernames (patch.local.json)
  private void signedInAs(UUID userId, String username) {
    given(currentUser.currentSubject()).willReturn(Optional.of(userId));
    given(currentUser.currentAccessToken()).willReturn(Optional.of(accessTokenOf(username)));
  }

  private static String accessTokenOf(String username) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", "monteis-spa");
    form.add("username", username);
    form.add("password", username);
    Map<?, ?> token =
        RestClient.create()
            .post()
            .uri(keycloak.getAuthServerUrl() + "/realms/monteis/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    return (String) token.get("access_token");
  }

  private static List<UUID> idsOf(List<DirectoryUser> users) {
    return users.stream().map(DirectoryUser::id).toList();
  }

  private static KeycloakContainer keycloakWithLocalRealm() {
    RealmRenderer.render(System.getProperty("monteis.repo.root", ".."));
    return new KeycloakContainer("quay.io/keycloak/keycloak:26.6.4")
        .withContextPath("/auth")
        .withRealmImportFile(RealmRenderer.CLASSPATH_RESOURCE_NAME);
  }

  @Configuration
  @EnableConfigurationProperties({KeycloakProperties.class, UserDirectoryProperties.class})
  @Import({KeycloakConfig.class, KeycloakUserDirectory.class})
  static class Config {

    @Bean
    RestClient.Builder restClientBuilder() {
      return RestClient.builder();
    }
  }
}
