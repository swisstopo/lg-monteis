package ch.swisstopo.monteis.core.modules.experiment.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerService;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentOwnerDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(ExperimentOwnerController.class)
class ExperimentOwnerControllerTest {

  private static final DirectoryUser ALICE =
      new DirectoryUser(UUID.randomUUID(), "Alice", "Example", "alice@example.test");
  private static final ExperimentOwnerDto ALICE_DTO =
      new ExperimentOwnerDto(ALICE.id(), ALICE.firstName(), ALICE.lastName(), ALICE.email());

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ExperimentOwnerService ownerService;
  @MockitoBean private ExperimentWebMapper mapper;

  @Test
  void should_list_visible_owners_for_every_reader() throws Exception {
    given(ownerService.findAllVisibleOwners()).willReturn(List.of(ALICE));
    given(mapper.toOwnerDtos(List.of(ALICE))).willReturn(List.of(ALICE_DTO));

    mockMvc
        .perform(
            get("/api/experiments/owners")
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(ALICE.id().toString()))
        .andExpect(jsonPath("$[0].lastName").value("Example"))
        .andExpect(jsonPath("$[0].email").value("alice@example.test"));
  }

  @Test
  void should_list_candidates_for_an_admin() throws Exception {
    given(ownerService.findOwnerCandidates(ASSIGNED_EXPERIMENT)).willReturn(List.of(ALICE));
    given(mapper.toOwnerDtos(List.of(ALICE))).willReturn(List.of(ALICE_DTO));

    mockMvc
        .perform(
            get("/api/experiments/{id}/owner-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].firstName").value("Alice"));
  }

  @Test
  void should_forbid_a_pi_to_list_candidates() throws Exception {
    mockMvc
        .perform(
            get("/api/experiments/{id}/owner-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());

    then(ownerService).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_503_when_keycloak_is_unavailable() throws Exception {
    given(ownerService.findOwnerCandidates(ASSIGNED_EXPERIMENT))
        .willThrow(new UserDirectoryUnavailableException("down"));

    mockMvc
        .perform(
            get("/api/experiments/{id}/owner-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.target").value("GLOBAL"))
        .andExpect(jsonPath("$.messageKey").value("error.user-directory.unavailable"));
  }

  @Test
  void should_answer_502_when_keycloak_denies() throws Exception {
    given(ownerService.findOwnerCandidates(ASSIGNED_EXPERIMENT))
        .willThrow(new UserDirectoryDeniedException("403"));

    mockMvc
        .perform(
            get("/api/experiments/{id}/owner-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.target").value("GLOBAL"))
        .andExpect(jsonPath("$.messageKey").value("error.user-directory.denied"));
  }
}
