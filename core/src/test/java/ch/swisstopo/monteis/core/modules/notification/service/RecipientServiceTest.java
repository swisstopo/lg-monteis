package ch.swisstopo.monteis.core.modules.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
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
    givenExperimentMembers(List.of(PI_ANNA, PI_BEN, READER_CARL), List.of(PI_ANNA, PI_BEN));
    given(recipientRepository.findSavedRecipientIdsOf(EXPERIMENT_ID)).willReturn(Optional.empty());

    // when
    Recipients recipients = service.findRecipientsOf(EXPERIMENT_ID);

    // then
    assertThat(recipients).isEqualTo(new Recipients(List.of(PI_ANNA, PI_BEN), false));
  }

  @Test
  void should_keep_a_saved_empty_list_empty_instead_of_taking_the_pis() {
    // given
    givenExperimentMembers(List.of(PI_ANNA, READER_CARL), List.of(PI_ANNA));
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
    givenExperimentMembers(List.of(PI_ANNA, PI_BEN, READER_CARL), List.of(PI_ANNA, PI_BEN));
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
    givenExperimentMembers(List.of(PI_ANNA), List.of(PI_ANNA));
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
    given(userDirectory.findMembersOf(EXPERIMENT_ID))
        .willReturn(new ExperimentMembers(List.of(PI_ANNA), List.of(PI_ANNA), true))
        .willReturn(new ExperimentMembers(List.of(PI_ANNA, READER_CARL), List.of(PI_ANNA), true));
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

  @Test
  void should_offer_the_members_of_the_experiment_as_candidates() {
    // given
    givenExperimentMembers(List.of(PI_ANNA, PI_BEN, READER_CARL), List.of(PI_ANNA, PI_BEN));

    // when
    List<DirectoryUser> candidates = service.findRecipientCandidatesOf(EXPERIMENT_ID);

    // then
    assertThat(candidates).containsExactly(PI_ANNA, PI_BEN, READER_CARL);
  }

  @Test
  void should_fail_to_list_candidates_of_an_unknown_experiment_without_asking_keycloak() {
    // given
    givenUnknownExperiment();

    // when / then
    assertThrows(
        ObjectNotFoundException.class, () -> service.findRecipientCandidatesOf(EXPERIMENT_ID));
    then(userDirectory).shouldHaveNoInteractions();
  }

  @Test
  void should_store_the_list_as_sent_and_answer_with_the_saved_recipients() {
    // given
    givenExperimentMembers(List.of(PI_ANNA, PI_BEN, READER_CARL), List.of(PI_ANNA, PI_BEN));

    // when
    Recipients recipients =
        service.replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_ANNA.id(), READER_CARL.id()));

    // then
    then(recipientRepository)
        .should()
        .replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_ANNA.id(), READER_CARL.id()));
    assertThat(recipients).isEqualTo(new Recipients(List.of(PI_ANNA, READER_CARL), true));
  }

  @Test
  void should_store_an_empty_list_as_saved() {
    // given
    givenExperimentMembers(List.of(PI_ANNA), List.of(PI_ANNA));

    // when
    Recipients recipients = service.replaceRecipientsOf(EXPERIMENT_ID, Set.of());

    // then
    then(recipientRepository).should().replaceRecipientsOf(EXPERIMENT_ID, Set.of());
    assertThat(recipients).isEqualTo(new Recipients(List.of(), true));
  }

  @Test
  void should_store_a_user_without_access_but_leave_them_out_of_the_answer() {
    // given
    givenExperimentMembers(List.of(PI_ANNA), List.of(PI_ANNA));

    // when
    Recipients recipients =
        service.replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_ANNA.id(), LEFT_DORA));

    // then
    then(recipientRepository)
        .should()
        .replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_ANNA.id(), LEFT_DORA));
    assertThat(recipients).isEqualTo(new Recipients(List.of(PI_ANNA), true));
  }

  @Test
  void should_keep_a_saved_recipient_who_lost_access_when_the_list_is_replaced() {
    // given
    givenExperimentMembers(List.of(PI_ANNA, PI_BEN), List.of(PI_ANNA, PI_BEN));
    given(recipientRepository.findSavedRecipientIdsOf(EXPERIMENT_ID))
        .willReturn(Optional.of(Set.of(PI_ANNA.id(), LEFT_DORA)));

    // when
    Recipients recipients = service.replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_BEN.id()));

    // then
    then(recipientRepository)
        .should()
        .replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_BEN.id(), LEFT_DORA));
    assertThat(recipients).isEqualTo(new Recipients(List.of(PI_BEN), true));
  }

  @Test
  void should_store_nothing_when_keycloak_is_unavailable() {
    // given
    given(userDirectory.findMembersOf(EXPERIMENT_ID))
        .willThrow(new UserDirectoryUnavailableException("down"));

    // when / then
    assertThrows(
        UserDirectoryUnavailableException.class,
        () -> service.replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_ANNA.id())));
    then(recipientRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_store_nothing_for_an_unknown_experiment() {
    // given
    givenUnknownExperiment();

    // when / then
    assertThrows(
        ObjectNotFoundException.class,
        () -> service.replaceRecipientsOf(EXPERIMENT_ID, Set.of(PI_ANNA.id())));
    then(recipientRepository).shouldHaveNoInteractions();
  }

  private void givenUnknownExperiment() {
    willThrow(new ObjectNotFoundException(Experiment.class))
        .given(experimentRepository)
        .requireVisible(EXPERIMENT_ID);
  }

  private void givenExperimentMembers(List<DirectoryUser> all, List<DirectoryUser> pis) {
    given(userDirectory.findMembersOf(EXPERIMENT_ID))
        .willReturn(new ExperimentMembers(all, pis, !pis.isEmpty()));
  }
}
