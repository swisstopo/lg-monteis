package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakConfig;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakProperties;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.itconfig.RealmRenderer;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// realm: docker/keycloak/realm, Alpha read + write = alice, Beta read = bob, Gamma read = alice
@Testcontainers
@SpringJUnitConfig(KeycloakUserDirectoryIT.Config.class)
class KeycloakUserDirectoryIT {

  private static final UUID ALICE = UUID.fromString("a11ce43c-8733-4a5e-a389-4760cbc21984");
  private static final UUID BOB = UUID.fromString("b0bb4079-a512-492e-bec7-ede4b410397c");

  @Container static final KeycloakContainer keycloak = keycloakWithLocalRealm();

  @Autowired private UserDirectory directory;
  @MockitoBean private CurrentUserProvider currentUser;

  @DynamicPropertySource
  static void keycloakApi(DynamicPropertyRegistry registry) {
    registry.add(
        "monteis.keycloak.api-uri", () -> keycloak.getAuthServerUrl() + "/admin/realms/monteis");
  }

  @BeforeEach
  void callAsKeycloakAdmin() {
    given(currentUser.currentSubject()).willReturn(Optional.of(UUID.randomUUID()));
    given(currentUser.currentAccessToken())
        .willReturn(
            Optional.of(keycloak.getKeycloakAdminClient().tokenManager().getAccessTokenString()));
  }

  @Test
  void should_find_the_members_of_the_read_and_write_group_of_an_experiment() {
    // when
    ExperimentMembers alpha = directory.findMembersOf(SeedData.EXPERIMENT_ALPHA);
    ExperimentMembers beta = directory.findMembersOf(SeedData.EXPERIMENT_BETA);
    ExperimentMembers gamma = directory.findMembersOf(SeedData.EXPERIMENT_GAMMA);

    // then
    assertThat(idsOf(alpha.pis())).containsExactly(ALICE);
    assertThat(idsOf(alpha.withAccess())).containsExactly(ALICE);
    assertThat(idsOf(beta.pis())).isEmpty();
    assertThat(idsOf(beta.withAccess())).containsExactly(BOB);
    assertThat(idsOf(gamma.withAccess())).containsExactly(ALICE);
  }

  @Test
  void should_find_the_contact_details_of_a_member() {
    // when
    DirectoryUser alice = directory.findMembersOf(SeedData.EXPERIMENT_ALPHA).pis().getFirst();

    // then
    assertThat(alice).isEqualTo(new DirectoryUser(ALICE, "Alice", "Example", "alice@example.test"));
  }

  @Test
  void should_find_no_members_of_an_experiment_without_groups() {
    // when
    ExperimentMembers members = directory.findMembersOf(UUID.randomUUID());

    // then
    assertThat(members).isEqualTo(new ExperimentMembers(List.of(), List.of()));
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
