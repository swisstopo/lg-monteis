package ch.swisstopo.monteis.core.modules.notification.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(RecipientController.class)
@Import(RecipientWebMapperImpl.class)
class RecipientControllerTest {

  private static final String RECIPIENTS = "/api/experiments/{id}/recipients";

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
}
