package ch.swisstopo.monteis.core.modules.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentUsers;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipientServiceTest {

  private static final UUID EXPERIMENT_ID = UUID.randomUUID();
  private static final DirectoryUser PI_ANNA =
      new DirectoryUser(UUID.randomUUID(), "Anna", "Amman", "anna@example.test");
  private static final DirectoryUser PI_BEN =
      new DirectoryUser(UUID.randomUUID(), "Ben", "Brunner", "ben@example.test");
  private static final DirectoryUser READER_CARL =
      new DirectoryUser(UUID.randomUUID(), "Carl", "Christen", "carl@example.test");
  private static final UUID LEFT_DORA = UUID.randomUUID();

  @Mock private ExperimentRepository experimentRepository;
  @Mock private RecipientRepository recipientRepository;
  @Mock private UserDirectory userDirectory;
  @InjectMocks private RecipientService service;

  @Test
  void should_take_the_pis_as_recipients_while_the_list_was_never_saved() {
    // given
    givenExperimentUsers(List.of(PI_ANNA, PI_BEN, READER_CARL), List.of(PI_ANNA, PI_BEN));
    given(recipientRepository.findSavedRecipientIdsOf(EXPERIMENT_ID)).willReturn(Optional.empty());

    // when
    Recipients recipients = service.findRecipientsOf(EXPERIMENT_ID);

    // then
    assertThat(recipients).isEqualTo(new Recipients(List.of(PI_ANNA, PI_BEN), false));
  }

  @Test
  void should_keep_a_saved_empty_list_empty_instead_of_taking_the_pis() {
    // given
    givenExperimentUsers(List.of(PI_ANNA, READER_CARL), List.of(PI_ANNA));
    given(recipientRepository.findSavedRecipientIdsOf(EXPERIMENT_ID))
        .willReturn(Optional.of(Set.of()));

    // when
    Recipients recipients = service.findRecipientsOf(EXPERIMENT_ID);

    // then
    assertThat(recipients).isEqualTo(new Recipients(List.of(), true));
  }

  @Test
  void should_take_the_saved_list_and_leave_out_a_pi_who_was_removed_from_it() {
    // given
    givenExperimentUsers(List.of(PI_ANNA, PI_BEN, READER_CARL), List.of(PI_ANNA, PI_BEN));
    given(recipientRepository.findSavedRecipientIdsOf(EXPERIMENT_ID))
        .willReturn(Optional.of(Set.of(PI_ANNA.id(), READER_CARL.id())));

    // when
    Recipients recipients = service.findRecipientsOf(EXPERIMENT_ID);

    // then
    assertThat(recipients).isEqualTo(new Recipients(List.of(PI_ANNA, READER_CARL), true));
  }

  @Test
  void should_leave_out_a_saved_recipient_who_lost_access() {
    // given
    givenExperimentUsers(List.of(PI_ANNA), List.of(PI_ANNA));
    given(recipientRepository.findSavedRecipientIdsOf(EXPERIMENT_ID))
        .willReturn(Optional.of(Set.of(PI_ANNA.id(), READER_CARL.id(), LEFT_DORA)));

    // when
    Recipients recipients = service.findRecipientsOf(EXPERIMENT_ID);

    // then
    assertThat(recipients).isEqualTo(new Recipients(List.of(PI_ANNA), true));
  }

  @Test
  void should_count_a_saved_recipient_again_once_their_access_is_back() {
    // given
    given(userDirectory.findExperimentUsersOf(EXPERIMENT_ID))
        .willReturn(new ExperimentUsers(List.of(PI_ANNA), List.of(PI_ANNA)))
        .willReturn(new ExperimentUsers(List.of(PI_ANNA, READER_CARL), List.of(PI_ANNA)));
    given(recipientRepository.findSavedRecipientIdsOf(EXPERIMENT_ID))
        .willReturn(Optional.of(Set.of(PI_ANNA.id(), READER_CARL.id())));

    // when
    Recipients withoutAccess = service.findRecipientsOf(EXPERIMENT_ID);
    Recipients accessBack = service.findRecipientsOf(EXPERIMENT_ID);

    // then
    assertThat(withoutAccess.users()).containsExactly(PI_ANNA);
    assertThat(accessBack.users()).containsExactly(PI_ANNA, READER_CARL);
  }

  @Test
  void should_fail_for_an_unknown_experiment_without_asking_keycloak() {
    // given
    willThrow(new ObjectNotFoundException(Experiment.class))
        .given(experimentRepository)
        .requireVisible(EXPERIMENT_ID);

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.findRecipientsOf(EXPERIMENT_ID));
    then(userDirectory).shouldHaveNoInteractions();
  }

  private void givenExperimentUsers(List<DirectoryUser> all, List<DirectoryUser> pis) {
    given(userDirectory.findExperimentUsersOf(EXPERIMENT_ID))
        .willReturn(new ExperimentUsers(all, pis));
  }
}
