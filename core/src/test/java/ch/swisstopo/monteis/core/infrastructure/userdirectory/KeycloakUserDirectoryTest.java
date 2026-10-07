package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAccessDeniedException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAdminClient;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakGroup;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUnavailableException;
import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakUser;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeycloakUserDirectoryTest {

  private static final UUID EXPERIMENT_ID = UUID.randomUUID();
  private static final String EXPERIMENT = EXPERIMENT_ID.toString();
  private static final String WRITE_IDS = KeycloakUserDirectory.WRITE_EXPERIMENT_IDS;
  private static final KeycloakGroup WRITE_GROUP =
      new KeycloakGroup("rw", "/Experiments/Alpha/read + write", null, null);
  private static final KeycloakUser ALICE =
      new KeycloakUser(UUID.randomUUID(), "Alice", "Zimmer", "alice@example.test", true);
  private static final KeycloakUser BOB =
      new KeycloakUser(UUID.randomUUID(), "Bob", "Amman", "bob@example.test", true);
  private static final KeycloakUser DISABLED =
      new KeycloakUser(UUID.randomUUID(), "Dan", "Disabled", null, false);

  @Mock private KeycloakAdminClient keycloak;
  @Mock private CurrentUserProvider currentUser;
  private KeycloakUserDirectory directory;

  @BeforeEach
  void setUp() {
    directory = new KeycloakUserDirectory(keycloak, currentUser, Duration.ofMinutes(1), 100);
    Mockito.lenient().when(currentUser.currentSubject()).thenReturn(Optional.of(UUID.randomUUID()));
  }

  @Test
  void should_find_the_enabled_members_of_the_write_groups_sorted_by_name() {
    givenWriteGroupWith(ALICE, DISABLED, BOB);

    assertEquals(
        new PrincipalInvestigators.Known(List.of(directoryUser(BOB), directoryUser(ALICE))),
        directory.principalInvestigatorsOf(EXPERIMENT_ID));
  }

  @Test
  void should_tell_a_missing_write_group() {
    given(keycloak.findGroupsByAttribute(WRITE_IDS, EXPERIMENT)).willReturn(List.of());

    assertEquals(
        new PrincipalInvestigators.NoWriteGroup(),
        directory.principalInvestigatorsOf(EXPERIMENT_ID));
  }

  @Test
  void should_tell_a_denial_apart_from_an_unavailable_keycloak() {
    given(keycloak.findGroupsByAttribute(WRITE_IDS, EXPERIMENT))
        .willThrow(new KeycloakAccessDeniedException("forbidden", null))
        .willThrow(new KeycloakUnavailableException("down", null));

    assertInstanceOf(
        PrincipalInvestigators.AccessDenied.class,
        directory.principalInvestigatorsOf(EXPERIMENT_ID));
    assertInstanceOf(
        PrincipalInvestigators.KeycloakUnavailable.class,
        directory.principalInvestigatorsOf(EXPERIMENT_ID));
  }

  @Test
  void should_cache_complete_answers_per_caller() {
    givenWriteGroupWith(ALICE);

    directory.principalInvestigatorsOf(EXPERIMENT_ID);
    directory.principalInvestigatorsOf(EXPERIMENT_ID);
    given(currentUser.currentSubject()).willReturn(Optional.of(UUID.randomUUID()));
    directory.principalInvestigatorsOf(EXPERIMENT_ID);

    then(keycloak).should(times(2)).groupMembers("rw");
  }

  @Test
  void should_not_cache_a_failed_lookup() {
    given(keycloak.findGroupsByAttribute(WRITE_IDS, EXPERIMENT))
        .willThrow(new KeycloakUnavailableException("down", null))
        .willReturn(List.of(WRITE_GROUP));
    given(keycloak.groupMembers("rw")).willReturn(List.of(ALICE));

    directory.principalInvestigatorsOf(EXPERIMENT_ID);

    assertInstanceOf(
        PrincipalInvestigators.Known.class, directory.principalInvestigatorsOf(EXPERIMENT_ID));
  }

  @Test
  void should_ask_keycloak_again_for_a_fresh_lookup() {
    givenWriteGroupWith(ALICE);

    directory.principalInvestigatorsOf(EXPERIMENT_ID);
    directory.currentPrincipalInvestigatorsOf(EXPERIMENT_ID);
    directory.principalInvestigatorsOf(EXPERIMENT_ID);

    then(keycloak).should(times(2)).groupMembers("rw");
  }

  @Test
  void should_not_ask_keycloak_without_a_caller() {
    given(currentUser.currentSubject()).willReturn(Optional.empty());

    assertInstanceOf(
        PrincipalInvestigators.KeycloakUnavailable.class,
        directory.principalInvestigatorsOf(EXPERIMENT_ID));
    then(keycloak).shouldHaveNoInteractions();
  }

  private void givenWriteGroupWith(KeycloakUser... members) {
    given(keycloak.findGroupsByAttribute(WRITE_IDS, EXPERIMENT)).willReturn(List.of(WRITE_GROUP));
    given(keycloak.groupMembers("rw")).willReturn(List.of(members));
  }

  private static DirectoryUser directoryUser(KeycloakUser user) {
    return new DirectoryUser(user.id(), user.firstName(), user.lastName(), user.email());
  }
}
