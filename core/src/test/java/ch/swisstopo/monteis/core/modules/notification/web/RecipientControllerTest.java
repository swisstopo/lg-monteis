package ch.swisstopo.monteis.core.modules.notification.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUserWebMapperImpl;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.ExperimentMembers;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectory;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.notification.domain.RecipientRepository;
import ch.swisstopo.monteis.core.modules.notification.service.RecipientService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ControllerTest(RecipientController.class)
@Import({RecipientService.class, RecipientWebMapperImpl.class, DirectoryUserWebMapperImpl.class})
class RecipientControllerTest {

  private static final String RECIPIENTS = "/api/experiments/{id}/recipients";

  private static final DirectoryUser PI_ANNA =
      new DirectoryUser(UUID.randomUUID(), "Anna", "Amman", "anna@example.test");
  private static final DirectoryUser READER_CARL =
      new DirectoryUser(UUID.randomUUID(), "Carl", "Christen", "carl@example.test");
  private static final UUID LEFT_DORA = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ExperimentRepository experimentRepository;
  @MockitoBean private RecipientRepository recipientRepository;
  @MockitoBean private UserDirectory userDirectory;

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"MONTEIS_ADMIN", "GLOBAL_EDITOR", "EXPERIMENT_PI"})
  void should_select_the_pis_while_the_list_was_never_saved(PrivilegeLevel level) throws Exception {
    // given
    givenMembers();
    given(recipientRepository.findSavedRecipientIdsOf(ASSIGNED_EXPERIMENT))
        .willReturn(Optional.empty());

    // when / then
    mockMvc
        .perform(get(RECIPIENTS, ASSIGNED_EXPERIMENT).with(authentication(level.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(false))
        .andExpect(jsonPath("$.candidates.length()").value(2))
        .andExpect(jsonPath("$.candidates[0].user.id").value(PI_ANNA.id().toString()))
        .andExpect(jsonPath("$.candidates[0].user.firstName").value("Anna"))
        .andExpect(jsonPath("$.candidates[0].user.lastName").value("Amman"))
        .andExpect(jsonPath("$.candidates[0].user.email").value("anna@example.test"))
        .andExpect(jsonPath("$.candidates[0].selected").value(true))
        .andExpect(jsonPath("$.candidates[1].user.id").value(READER_CARL.id().toString()))
        .andExpect(jsonPath("$.candidates[1].selected").value(false));
  }

  @Test
  void should_select_the_saved_recipients_and_leave_out_those_who_lost_access() throws Exception {
    // given
    givenMembers();
    given(recipientRepository.findSavedRecipientIdsOf(ASSIGNED_EXPERIMENT))
        .willReturn(Optional.of(Set.of(READER_CARL.id(), LEFT_DORA)));

    // when / then
    mockMvc
        .perform(getRecipientsAsPi())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true))
        .andExpect(jsonPath("$.candidates.length()").value(2))
        .andExpect(jsonPath("$.candidates[0].selected").value(false))
        .andExpect(jsonPath("$.candidates[1].user.id").value(READER_CARL.id().toString()))
        .andExpect(jsonPath("$.candidates[1].selected").value(true));
  }

  @Test
  void should_select_nobody_for_a_saved_empty_list() throws Exception {
    // given
    givenMembers();
    given(recipientRepository.findSavedRecipientIdsOf(ASSIGNED_EXPERIMENT))
        .willReturn(Optional.of(Set.of()));

    // when / then
    mockMvc
        .perform(getRecipientsAsPi())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true))
        .andExpect(jsonPath("$.candidates[0].selected").value(false))
        .andExpect(jsonPath("$.candidates[1].selected").value(false));
  }

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"BASISROLLE", "EXPERIMENT_USER"})
  void should_forbid_a_caller_without_write_access(PrivilegeLevel level) throws Exception {
    // when / then
    mockMvc
        .perform(get(RECIPIENTS, ASSIGNED_EXPERIMENT).with(authentication(level.authentication())))
        .andExpect(status().isForbidden());

    then(userDirectory).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_of_another_experiment() throws Exception {
    // when / then
    mockMvc
        .perform(
            get(RECIPIENTS, OTHER_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());

    then(userDirectory).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_who_only_reads_the_experiment() throws Exception {
    // given
    givenReadOnlyExperiment();

    // when / then
    mockMvc.perform(getRecipientsAsPi()).andExpect(status().isForbidden());

    then(userDirectory).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_404_for_an_unknown_experiment() throws Exception {
    // given
    UUID unknown = givenUnknownExperiment();

    // when / then
    mockMvc
        .perform(
            get(RECIPIENTS, unknown)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
  }

  @Test
  void should_answer_503_when_keycloak_is_unavailable() throws Exception {
    // given
    givenKeycloakUnavailable();

    // when / then
    mockMvc
        .perform(getRecipientsAsPi())
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.messageKey").value(UserDirectoryUnavailableException.MESSAGE_KEY));
  }

  @Test
  void should_not_offer_the_removed_candidates_endpoint() throws Exception {
    // when / then
    mockMvc
        .perform(
            get("/api/experiments/{id}/recipient-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"MONTEIS_ADMIN", "GLOBAL_EDITOR", "EXPERIMENT_PI"})
  void should_replace_the_recipients_and_answer_like_the_get(PrivilegeLevel level)
      throws Exception {
    // given
    givenMembers();
    given(recipientRepository.findSavedRecipientIdsOf(ASSIGNED_EXPERIMENT))
        .willReturn(Optional.empty());

    // when / then
    mockMvc
        .perform(
            replaceRecipients(ASSIGNED_EXPERIMENT, userIdsJson(READER_CARL.id(), READER_CARL.id()))
                .with(authentication(level.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true))
        .andExpect(jsonPath("$.candidates.length()").value(2))
        .andExpect(jsonPath("$.candidates[0].user.id").value(PI_ANNA.id().toString()))
        .andExpect(jsonPath("$.candidates[0].selected").value(false))
        .andExpect(jsonPath("$.candidates[1].user.email").value("carl@example.test"))
        .andExpect(jsonPath("$.candidates[1].selected").value(true));
    then(recipientRepository)
        .should()
        .replaceRecipientsOf(ASSIGNED_EXPERIMENT, Set.of(READER_CARL.id()));
  }

  @Test
  void should_accept_an_empty_list() throws Exception {
    // given
    givenMembers();
    given(recipientRepository.findSavedRecipientIdsOf(ASSIGNED_EXPERIMENT))
        .willReturn(Optional.empty());

    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(userIdsJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true))
        .andExpect(jsonPath("$.candidates[0].selected").value(false))
        .andExpect(jsonPath("$.candidates[1].selected").value(false));
    then(recipientRepository).should().replaceRecipientsOf(ASSIGNED_EXPERIMENT, Set.of());
  }

  @Test
  void should_keep_a_saved_recipient_who_lost_access() throws Exception {
    // given
    givenMembers();
    given(recipientRepository.findSavedRecipientIdsOf(ASSIGNED_EXPERIMENT))
        .willReturn(Optional.of(Set.of(PI_ANNA.id(), LEFT_DORA)));

    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(userIdsJson(READER_CARL.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates.length()").value(2));
    then(recipientRepository)
        .should()
        .replaceRecipientsOf(ASSIGNED_EXPERIMENT, Set.of(READER_CARL.id(), LEFT_DORA));
  }

  @Test
  void should_reject_a_user_who_is_no_candidate_with_422() throws Exception {
    // given
    givenMembers();
    UUID stranger = UUID.randomUUID();

    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(userIdsJson(PI_ANNA.id(), stranger)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.field").value("userIds"))
        .andExpect(jsonPath("$.messageKey").value("experiment.owner.not-eligible"));
    then(recipientRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_a_saved_recipient_who_lost_access_when_sent_again() throws Exception {
    // given
    givenMembers();

    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(userIdsJson(LEFT_DORA)))
        .andExpect(status().isUnprocessableContent());
    then(recipientRepository).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"BASISROLLE", "EXPERIMENT_USER"})
  void should_forbid_a_caller_without_write_access_to_replace_the_recipients(PrivilegeLevel level)
      throws Exception {
    // when / then
    mockMvc
        .perform(
            replaceRecipients(ASSIGNED_EXPERIMENT, userIdsJson(PI_ANNA.id()))
                .with(authentication(level.authentication())))
        .andExpect(status().isForbidden());

    then(recipientRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_of_another_experiment_to_replace_the_recipients() throws Exception {
    // when / then
    mockMvc
        .perform(
            replaceRecipients(OTHER_EXPERIMENT, userIdsJson(PI_ANNA.id()))
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());

    then(recipientRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_who_only_reads_the_experiment_to_replace_the_recipients()
      throws Exception {
    // given
    givenReadOnlyExperiment();

    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(userIdsJson(PI_ANNA.id())))
        .andExpect(status().isForbidden());

    then(recipientRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_404_and_store_nothing_for_an_unknown_experiment() throws Exception {
    // given
    UUID unknown = givenUnknownExperiment();

    // when / then
    mockMvc
        .perform(
            replaceRecipients(unknown, userIdsJson(PI_ANNA.id()))
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
    then(recipientRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_503_and_store_nothing_when_keycloak_is_unavailable() throws Exception {
    // given
    givenKeycloakUnavailable();

    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(userIdsJson(PI_ANNA.id())))
        .andExpect(status().isServiceUnavailable());
    then(recipientRepository).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"userIds\":null}", "{\"userIds\":[null]}"})
  void should_reject_a_missing_list_or_a_missing_id_with_422(String body) throws Exception {
    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(body))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$[0].messageKey").value("validation.required"));

    then(recipientRepository).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"userIds\":[\"not-a-uuid\"]}", "[]", "not json"})
  void should_reject_a_malformed_body_with_400(String body) throws Exception {
    // when / then
    mockMvc
        .perform(replaceRecipientsAsPi(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageKey").value("error.system.internal"));

    then(recipientRepository).shouldHaveNoInteractions();
  }

  private void givenMembers() {
    given(userDirectory.findMembersOf(ASSIGNED_EXPERIMENT))
        .willReturn(new ExperimentMembers(List.of(PI_ANNA, READER_CARL), List.of(PI_ANNA), true));
  }

  private void givenKeycloakUnavailable() {
    given(userDirectory.findMembersOf(ASSIGNED_EXPERIMENT))
        .willThrow(new UserDirectoryUnavailableException("down"));
  }

  // the filter chain lets the PI through, like once MON-196 drops its per-experiment rule
  private void givenReadOnlyExperiment() {
    willThrow(new AccessDeniedException("read only"))
        .given(experimentRepository)
        .requireWritable(ASSIGNED_EXPERIMENT);
  }

  private UUID givenUnknownExperiment() {
    UUID unknown = UUID.randomUUID();
    willThrow(new ObjectNotFoundException(Experiment.class))
        .given(experimentRepository)
        .requireWritable(unknown);
    return unknown;
  }

  private static MockHttpServletRequestBuilder getRecipientsAsPi() {
    return get(RECIPIENTS, ASSIGNED_EXPERIMENT)
        .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication()));
  }

  private static MockHttpServletRequestBuilder replaceRecipientsAsPi(String body) {
    return replaceRecipients(ASSIGNED_EXPERIMENT, body)
        .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication()));
  }

  private static MockHttpServletRequestBuilder replaceRecipients(UUID experimentId, String body) {
    return put(RECIPIENTS, experimentId)
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private static String userIdsJson(UUID... userIds) {
    String ids =
        Arrays.stream(userIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
    return "{\"userIds\":[" + ids + "]}";
  }
}
