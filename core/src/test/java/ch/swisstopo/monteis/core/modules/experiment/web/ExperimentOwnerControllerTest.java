package ch.swisstopo.monteis.core.modules.experiment.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryDeniedException;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.UserDirectoryUnavailableException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.OwnersStatus;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerService;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentWithOwners;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentOwnerDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUpClock() {
    given(clock.instant()).willReturn(Instant.parse("2024-01-01T12:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("UTC"));
  }

  @Test
  void should_list_assigned_owners_for_every_reader() throws Exception {
    given(ownerService.filterableOwners()).willReturn(List.of(ALICE));
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
    given(ownerService.ownerCandidates(ASSIGNED_EXPERIMENT)).willReturn(List.of(ALICE));
    given(mapper.toOwnerDtos(List.of(ALICE))).willReturn(List.of(ALICE_DTO));

    mockMvc
        .perform(
            get("/api/experiments/{id}/owner-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].firstName").value("Alice"));
  }

  @Test
  void should_forbid_a_pi_to_list_candidates_or_change_owners() throws Exception {
    mockMvc
        .perform(
            get("/api/experiments/{id}/owner-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/experiments/{id}/owners", ASSIGNED_EXPERIMENT)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownersBody(ALICE.id())))
        .andExpect(status().isForbidden());

    then(ownerService).shouldHaveNoInteractions();
  }

  @Test
  void should_replace_owners_and_return_the_experiment() throws Exception {
    Experiment updated = mock(Experiment.class);
    ExperimentResponseDto response =
        new ExperimentResponseDto(
            ASSIGNED_EXPERIMENT,
            "EXP",
            null,
            null,
            null,
            2,
            0,
            List.of(ALICE_DTO),
            OwnersStatus.SHOWN);
    given(ownerService.replaceOwners(ASSIGNED_EXPERIMENT, Set.of(ALICE.id()))).willReturn(updated);
    ExperimentWithOwners withOwners =
        new ExperimentWithOwners(updated, VisibleOwners.of(List.of(ALICE)));
    given(ownerService.withOwners(updated)).willReturn(withOwners);
    given(mapper.toDto(eq(withOwners), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/experiments/{id}/owners", ASSIGNED_EXPERIMENT)
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownersBody(ALICE.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owners[0].id").value(ALICE.id().toString()));
  }

  @Test
  void should_reject_a_request_without_owner_ids() throws Exception {
    mockMvc
        .perform(
            put("/api/experiments/{id}/owners", ASSIGNED_EXPERIMENT)
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().is4xxClientError());

    then(ownerService).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_422_on_the_owner_ids_field_for_a_user_that_is_not_a_pi() throws Exception {
    given(ownerService.replaceOwners(any(), any()))
        .willThrow(
            new FieldBusinessValidationException(
                ExperimentOwnerService.OWNER_IDS_FIELD,
                Set.of(ALICE.id()),
                ExperimentOwnerService.NOT_ELIGIBLE_KEY,
                Map.of()));

    mockMvc
        .perform(
            put("/api/experiments/{id}/owners", ASSIGNED_EXPERIMENT)
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ownersBody(ALICE.id())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.field").value("ownerIds"))
        .andExpect(jsonPath("$.messageKey").value("experiment.owner.not-eligible"));
  }

  @Test
  void should_answer_503_when_keycloak_is_unavailable() throws Exception {
    given(ownerService.ownerCandidates(ASSIGNED_EXPERIMENT))
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
    given(ownerService.ownerCandidates(ASSIGNED_EXPERIMENT))
        .willThrow(new UserDirectoryDeniedException("403"));

    mockMvc
        .perform(
            get("/api/experiments/{id}/owner-candidates", ASSIGNED_EXPERIMENT)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.target").value("GLOBAL"))
        .andExpect(jsonPath("$.messageKey").value("error.user-directory.denied"));
  }

  private static String ownersBody(UUID... ownerIds) {
    List<String> quoted = Arrays.stream(ownerIds).map(id -> "\"" + id + "\"").toList();
    return "{\"ownerIds\":[" + String.join(",", quoted) + "]}";
  }
}
