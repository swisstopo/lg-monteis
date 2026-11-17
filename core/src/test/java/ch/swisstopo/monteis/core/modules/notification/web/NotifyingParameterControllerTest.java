package ch.swisstopo.monteis.core.modules.notification.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.notification.service.NotifyingParameterService;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotifyingParameterResponseDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(NotifyingParameterController.class)
class NotifyingParameterControllerTest {

  private static final String NOTIFYING_PARAMETERS = "/api/experiments/{id}/notifying-parameters";

  private static final NotifyingParameterResponseDto TEMPERATURE =
      new NotifyingParameterResponseDto(
          UUID.fromString("00000000-0000-7000-8000-000000000401"),
          "monteis-001",
          "Temperature Param",
          "SOL_EXPERTS__TEMP-1__temperature",
          "KELVIN",
          OffsetDateTime.of(2026, 11, 16, 8, 30, 0, 0, ZoneOffset.UTC),
          12.5,
          true,
          false,
          MeasurementStatus.TOO_LOW);

  private static final NotifyingParameterResponseDto WITHOUT_READING =
      new NotifyingParameterResponseDto(
          UUID.fromString("00000000-0000-7000-8000-000000000402"),
          "monteis-002",
          "Pressure Param",
          "SOL_EXPERTS__PRESS-1&2__pressure",
          "KILOGRAM",
          null,
          null,
          true,
          true,
          null);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotifyingParameterService service;

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"MONTEIS_ADMIN", "GLOBAL_EDITOR", "EXPERIMENT_PI"})
  void should_list_the_notifying_parameters_for_a_writer_of_the_experiment(PrivilegeLevel level)
      throws Exception {
    // given
    given(service.findNotifyingParametersOf(ASSIGNED_EXPERIMENT))
        .willReturn(List.of(TEMPERATURE, WITHOUT_READING));

    // when / then
    mockMvc
        .perform(
            get(NOTIFYING_PARAMETERS, ASSIGNED_EXPERIMENT)
                .with(authentication(level.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(
            jsonPath("$[0].sensorParameterId").value(TEMPERATURE.sensorParameterId().toString()))
        .andExpect(jsonPath("$[0].sensorName").value("monteis-001"))
        .andExpect(jsonPath("$[0].sensorParameterName").value("Temperature Param"))
        .andExpect(jsonPath("$[0].dasKey").value("SOL_EXPERTS__TEMP-1__temperature"))
        .andExpect(jsonPath("$[0].unit").value("KELVIN"))
        .andExpect(jsonPath("$[0].newestMeasurement").value("2026-11-16T08:30:00Z"))
        .andExpect(jsonPath("$[0].measureValue").value(12.5))
        .andExpect(jsonPath("$[0].sensorActive").value(true))
        .andExpect(jsonPath("$[0].sensorParameterActive").value(false))
        .andExpect(jsonPath("$[0].measurementStatus").value("TOO_LOW"))
        .andExpect(jsonPath("$[1].newestMeasurement").doesNotExist())
        .andExpect(jsonPath("$[1].measureValue").doesNotExist())
        .andExpect(jsonPath("$[1].measurementStatus").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(
      value = PrivilegeLevel.class,
      names = {"BASISROLLE", "EXPERIMENT_USER"})
  void should_forbid_a_caller_without_write_access(PrivilegeLevel level) throws Exception {
    // when / then
    mockMvc
        .perform(
            get(NOTIFYING_PARAMETERS, ASSIGNED_EXPERIMENT)
                .with(authentication(level.authentication())))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_of_another_experiment() throws Exception {
    // when / then
    mockMvc
        .perform(
            get(NOTIFYING_PARAMETERS, OTHER_EXPERIMENT)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_answer_404_for_an_unknown_experiment() throws Exception {
    // given
    UUID unknown = UUID.randomUUID();
    given(service.findNotifyingParametersOf(unknown))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    // when / then
    mockMvc
        .perform(
            get(NOTIFYING_PARAMETERS, unknown)
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageKey").value("object.not-found"));
  }
}
