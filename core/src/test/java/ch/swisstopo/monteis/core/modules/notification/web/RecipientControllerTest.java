package ch.swisstopo.monteis.core.modules.notification.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.notification.domain.Recipients;
import ch.swisstopo.monteis.core.modules.notification.service.RecipientService;
import java.util.Arrays;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ControllerTest(RecipientController.class)
@Import(RecipientWebMapperImpl.class)
class RecipientControllerTest {

  private static final String RECIPIENTS = "/api/experiments/{id}/recipients";
  private static final String CANDIDATES = "/api/experiments/{id}/recipient-candidates";

  private static final DirectoryUser ANNA =
      new DirectoryUser(UUID.randomUUID(), "Anna", "Amman", "anna@example.test");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RecipientService service;

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"MONTEIS_ADMIN", "GLOBAL_EDITOR", "EXPERIMENT_PI"})
  void should_list_the_recipients_with_their_contact_details_for_a_writer_of_the_experiment(
      PrivilegeLevel level) throws Exception {
    // given
    given(service.findRecipientsOf(ASSIGNED_EXPERIMENT))
        .willReturn(new Recipients(List.of(ANNA), true));

    // when / then
    mockMvc
        .perform(get(RECIPIENTS, ASSIGNED_EXPERIMENT).with(authentication(level.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true))
        .andExpect(jsonPath("$.recipients.length()").value(1))
        .andExpect(jsonPath("$.recipients[0].id").value(ANNA.id().toString()))
        .andExpect(jsonPath("$.recipients[0].firstName").value("Anna"))
        .andExpect(jsonPath("$.recipients[0].lastName").value("Amman"))
        .andExpect(jsonPath("$.recipients[0].email").value("anna@example.test"));
  }

  @Test
  void should_tell_a_list_that_was_never_saved() throws Exception {
    // given
    given(service.findRecipientsOf(ASSIGNED_EXPERIMENT))
        .willReturn(new Recipients(List.of(ANNA), false));

    // when / then
    mockMvc
        .perform(
            get(RECIPIENTS, ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(false))
        .andExpect(jsonPath("$.recipients[0].id").value(ANNA.id().toString()));
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

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_of_another_experiment() throws Exception {
    // when / then
    mockMvc
        .perform(
            get(RECIPIENTS, OTHER_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_404_for_an_unknown_experiment() throws Exception {
    // given
    UUID unknown = UUID.randomUUID();
    given(service.findRecipientsOf(unknown))
        .willThrow(new ObjectNotFoundException(Experiment.class));

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
    given(service.findRecipientsOf(ASSIGNED_EXPERIMENT))
        .willThrow(new UserDirectoryUnavailableException("down"));

    // when / then
    mockMvc
        .perform(
            get(RECIPIENTS, ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.messageKey").value(UserDirectoryUnavailableException.MESSAGE_KEY));
  }

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"MONTEIS_ADMIN", "GLOBAL_EDITOR", "EXPERIMENT_PI"})
  void should_replace_the_recipients_for_a_writer_of_the_experiment(PrivilegeLevel level)
      throws Exception {
    // given
    given(service.replaceRecipientsOf(ASSIGNED_EXPERIMENT, Set.of(ANNA.id())))
        .willReturn(new Recipients(List.of(ANNA), true));

    // when / then
    mockMvc
        .perform(
            replaceRecipients(ASSIGNED_EXPERIMENT, userIdsJson(ANNA.id(), ANNA.id()))
                .with(authentication(level.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true))
        .andExpect(jsonPath("$.recipients.length()").value(1))
        .andExpect(jsonPath("$.recipients[0].id").value(ANNA.id().toString()))
        .andExpect(jsonPath("$.recipients[0].email").value("anna@example.test"));
  }

  @Test
  void should_accept_an_empty_list() throws Exception {
    // given
    given(service.replaceRecipientsOf(ASSIGNED_EXPERIMENT, Set.of()))
        .willReturn(new Recipients(List.of(), true));

    // when / then
    mockMvc
        .perform(
            replaceRecipients(ASSIGNED_EXPERIMENT, userIdsJson())
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saved").value(true))
        .andExpect(jsonPath("$.recipients").isEmpty());
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
            replaceRecipients(ASSIGNED_EXPERIMENT, userIdsJson(ANNA.id()))
                .with(authentication(level.authentication())))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_of_another_experiment_to_replace_the_recipients() throws Exception {
    // when / then
    mockMvc
        .perform(
            replaceRecipients(OTHER_EXPERIMENT, userIdsJson(ANNA.id()))
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_404_when_replacing_the_recipients_of_an_unknown_experiment() throws Exception {
    // given
    UUID unknown = UUID.randomUUID();
    given(service.replaceRecipientsOf(unknown, Set.of(ANNA.id())))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    // when / then
    mockMvc
        .perform(
            replaceRecipients(unknown, userIdsJson(ANNA.id()))
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"userIds\":null}", "{\"userIds\":[null]}"})
  void should_reject_a_missing_list_or_a_missing_id_with_422(String body) throws Exception {
    // when / then
    mockMvc
        .perform(
            replaceRecipients(ASSIGNED_EXPERIMENT, body)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$[0].messageKey").value("validation.required"));

    then(service).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"userIds\":[\"not-a-uuid\"]}", "[]", "not json"})
  void should_reject_a_malformed_body_with_400(String body) throws Exception {
    // when / then
    mockMvc
        .perform(
            replaceRecipients(ASSIGNED_EXPERIMENT, body)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageKey").value("error.system.internal"));

    then(service).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"MONTEIS_ADMIN", "GLOBAL_EDITOR", "EXPERIMENT_PI"})
  void should_list_the_candidates_with_their_contact_details_for_a_writer_of_the_experiment(
      PrivilegeLevel level) throws Exception {
    // given
    given(service.findRecipientCandidatesOf(ASSIGNED_EXPERIMENT)).willReturn(List.of(ANNA));

    // when / then
    mockMvc
        .perform(get(CANDIDATES, ASSIGNED_EXPERIMENT).with(authentication(level.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(ANNA.id().toString()))
        .andExpect(jsonPath("$[0].firstName").value("Anna"))
        .andExpect(jsonPath("$[0].lastName").value("Amman"))
        .andExpect(jsonPath("$[0].email").value("anna@example.test"));
  }

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"BASISROLLE", "EXPERIMENT_USER"})
  void should_forbid_a_caller_without_write_access_to_list_the_candidates(PrivilegeLevel level)
      throws Exception {
    // when / then
    mockMvc
        .perform(get(CANDIDATES, ASSIGNED_EXPERIMENT).with(authentication(level.authentication())))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_of_another_experiment_to_list_the_candidates() throws Exception {
    // when / then
    mockMvc
        .perform(
            get(CANDIDATES, OTHER_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_404_for_the_candidates_of_an_unknown_experiment() throws Exception {
    // given
    UUID unknown = UUID.randomUUID();
    given(service.findRecipientCandidatesOf(unknown))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    // when / then
    mockMvc
        .perform(
            get(CANDIDATES, unknown)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
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
