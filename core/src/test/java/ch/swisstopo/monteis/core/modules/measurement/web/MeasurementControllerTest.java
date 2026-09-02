package ch.swisstopo.monteis.core.modules.measurement.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.measurement.service.MeasurementService;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(MeasurementController.class)
class MeasurementControllerTest {

  private static final UUID SENSOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MeasurementService measurementService;

  // A valid, ascending, past date pair reused by tests that only care about the happy path.
  private final OffsetDateTime validFrom = OffsetDateTime.parse("2024-01-01T00:00:00Z");
  private final OffsetDateTime validTo = OffsetDateTime.parse("2024-01-02T00:00:00Z");

  @Test
  void should_return_chart_data_when_request_is_valid() throws Exception {
    // given
    ChartDataResponseDto dto =
        new ChartDataResponseDto(
            SENSOR_ID,
            "TEMP-1",
            "monteis-001",
            Unit.KELVIN,
            List.of(new ChartPointDto(validFrom, 12.5)));
    given(measurementService.findMeasurements(SENSOR_ID, validFrom, validTo))
        .willReturn(Optional.of(dto));

    // when / then: one sensor per request, so the body is an object rather than an array
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("id", SENSOR_ID.toString())
                .param("from", validFrom.toString())
                .param("to", validTo.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(SENSOR_ID.toString()))
        .andExpect(jsonPath("$.sensorCode").value("TEMP-1"))
        .andExpect(jsonPath("$.sensorName").value("monteis-001"))
        .andExpect(jsonPath("$.unit").value("KELVIN"))
        .andExpect(jsonPath("$.data[0].value").value(12.5));

    then(measurementService).should().findMeasurements(SENSOR_ID, validFrom, validTo);
  }

  @Test
  void should_return_404_when_sensor_is_absent_or_invisible() throws Exception {
    // given: the service reports absent and hidden-by-RLS identically, so the API cannot be
    // used to probe whether a sensor the caller may not see exists
    UUID unknownId = UUID.randomUUID();
    given(measurementService.findMeasurements(unknownId, validFrom, validTo))
        .willReturn(Optional.empty());

    // when / then
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("id", unknownId.toString())
                .param("from", validFrom.toString())
                .param("to", validTo.toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void should_return_401_when_not_authenticated() throws Exception {
    // when / then: no .with(jwt()) -> the request carries no credentials at all
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .param("id", SENSOR_ID.toString())
                .param("from", validFrom.toString())
                .param("to", validTo.toString()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_return_400_when_id_param_is_missing() throws Exception {
    // when / then
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("from", validFrom.toString())
                .param("to", validTo.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_400_when_id_is_not_a_valid_uuid() throws Exception {
    // when / then
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("id", "not-a-uuid")
                .param("from", validFrom.toString())
                .param("to", validTo.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_400_when_from_is_in_the_future() throws Exception {
    // given: @PastOrPresent rejects any instant strictly after now
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);

    // when / then
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("id", SENSOR_ID.toString())
                .param("from", future.toString())
                .param("to", validTo.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_400_when_to_is_in_the_future() throws Exception {
    // given
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);

    // when / then
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("id", SENSOR_ID.toString())
                .param("from", validFrom.toString())
                .param("to", future.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_200_when_from_equals_to() throws Exception {
    // given: the boundary the service guard explicitly allows (only "after" is rejected)
    ChartDataResponseDto dto =
        new ChartDataResponseDto(SENSOR_ID, "TEMP-1", "monteis-001", Unit.KELVIN, List.of());
    given(measurementService.findMeasurements(SENSOR_ID, validFrom, validFrom))
        .willReturn(Optional.of(dto));

    // when / then
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("id", SENSOR_ID.toString())
                .param("from", validFrom.toString())
                .param("to", validFrom.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  void should_return_422_when_service_rejects_from_after_to() throws Exception {
    // given: the controller performs no from<=to check itself, it trusts the service to enforce
    // it, so this simulates the service's ObjectBusinessValidationException reaching the client
    OffsetDateTime laterDate = validTo;
    OffsetDateTime earlierDate = validFrom;
    given(measurementService.findMeasurements(SENSOR_ID, laterDate, earlierDate))
        .willThrow(
            new ObjectBusinessValidationException(
                "measurement.dateRange.invalid", Map.of("from", laterDate, "to", earlierDate)));

    // when / then: swap from/to in the request so from > to
    mockMvc
        .perform(
            get("/api/measurements/charts/data")
                .with(jwt())
                .param("id", SENSOR_ID.toString())
                .param("from", laterDate.toString())
                .param("to", earlierDate.toString()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.messageKey").value("measurement.dateRange.invalid"));
  }
}
