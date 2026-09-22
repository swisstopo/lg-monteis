package ch.swisstopo.monteis.core.modules.measurement.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.measurement.service.MeasurementService;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.MeasurementResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
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

  @MockitoBean private PagedRequestParser pagedRequestParser;

  // A valid, ascending, past date pair reused by tests that only care about the happy path.
  private final OffsetDateTime validFrom = OffsetDateTime.parse("2024-01-01T00:00:00Z");
  private final OffsetDateTime validTo = OffsetDateTime.parse("2024-01-02T00:00:00Z");

  @Test
  void should_return_chart_data_when_request_is_valid() throws Exception {
    // given
    ChartDataResponseDto dto =
        new ChartDataResponseDto(
            SENSOR_ID,
            "SOL_EXPERTS__TEMP-1__pressure",
            "monteis-001",
            Unit.KELVIN,
            List.of(new ChartPointDto(validFrom, 12.5)));
    given(measurementService.findChartData(SENSOR_ID, validFrom, validTo))
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
        .andExpect(jsonPath("$.dasKey").value("SOL_EXPERTS__TEMP-1__pressure"))
        .andExpect(jsonPath("$.name").value("monteis-001"))
        .andExpect(jsonPath("$.unit").value("KELVIN"))
        .andExpect(jsonPath("$.points[0].value").value(12.5));

    then(measurementService).should().findChartData(SENSOR_ID, validFrom, validTo);
  }

  @Test
  void should_return_404_when_sensor_is_absent_or_invisible() throws Exception {
    // given: the service reports absent and hidden-by-RLS identically, so the API cannot be
    // used to probe whether a sensor the caller may not see exists
    UUID unknownId = UUID.randomUUID();
    given(measurementService.findChartData(unknownId, validFrom, validTo))
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
        new ChartDataResponseDto(
            SENSOR_ID, "SOL_EXPERTS__TEMP-1__pressure", "monteis-001", Unit.KELVIN, List.of());
    given(measurementService.findChartData(SENSOR_ID, validFrom, validFrom))
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
        .andExpect(jsonPath("$.points").isArray());
  }

  @Test
  void should_return_422_when_service_rejects_from_after_to() throws Exception {
    // given: the controller performs no from<=to check itself, it trusts the service to enforce
    // it, so this simulates the service's ObjectBusinessValidationException reaching the client
    OffsetDateTime laterDate = validTo;
    OffsetDateTime earlierDate = validFrom;
    given(measurementService.findChartData(SENSOR_ID, laterDate, earlierDate))
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

  @Test
  void should_return_measurements_and_map_paged_result_to_json() throws Exception {
    // given
    MeasurementResponseDto row =
        new MeasurementResponseDto(
            SENSOR_ID,
            "SOL_EXPERTS__TEMP-1__temperature",
            "Mont Terri Alpha",
            "monteis-001",
            "Temperature Param",
            validFrom,
            12.5,
            "KELVIN",
            "Temperature",
            new CoordinatesDto(100.0, 200.0, 300.0),
            new AlarmLimitsDto(-50.0, 100.0),
            true,
            "Air temperature sensor near ventilation intake",
            List.of(new ChartPointDto(validFrom, 12.5)),
            "To High");
    PagedRequest parsed = new PagedRequest(0, 20, List.of(), Map.of());
    given(pagedRequestParser.parse(any())).willReturn(parsed);
    given(measurementService.getPagedMeasurements(parsed))
        .willReturn(new PagedResult<>(List.of(row), 1));

    // when / then
    mockMvc
        .perform(
            get("/api/measurements")
                .with(jwt())
                .queryParam("startRow", "0")
                .queryParam("endRow", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.rows[0].sensorParameterId").value(SENSOR_ID.toString()))
        .andExpect(jsonPath("$.rows[0].dasKey").value("SOL_EXPERTS__TEMP-1__temperature"))
        .andExpect(jsonPath("$.rows[0].sensorName").value("monteis-001"))
        .andExpect(jsonPath("$.rows[0].trend[0].value").value(12.5));

    then(pagedRequestParser).should().parse(any());
    then(measurementService).should().getPagedMeasurements(parsed);
  }

  @Test
  void should_pass_raw_start_row_end_row_sort_and_filter_models_to_parser() throws Exception {
    // given
    PagedRequest parsed = new PagedRequest(0, 20, List.of(), Map.of());
    given(pagedRequestParser.parse(any())).willReturn(parsed);
    given(measurementService.getPagedMeasurements(parsed))
        .willReturn(new PagedResult<>(List.of(), 0));

    String sortModel = "[{\"colId\":\"sensorName\",\"sort\":\"asc\"}]";
    String filterModel = "{\"active\":{\"filterType\":\"set\",\"values\":[\"true\"]}}";

    // when
    mockMvc
        .perform(
            get("/api/measurements")
                .with(jwt())
                .queryParam("startRow", "0")
                .queryParam("endRow", "20")
                .queryParam("sortModel", sortModel)
                .queryParam("filterModel", filterModel))
        .andExpect(status().isOk());

    // then: the raw query parameters reach the parser untouched, parsing itself is the
    // parser's responsibility
    then(pagedRequestParser).should().parse(new RawPagedRequest(0, 20, sortModel, filterModel));
  }

  @Test
  void should_return_400_when_start_row_is_negative() throws Exception {
    // when / then
    mockMvc
        .perform(
            get("/api/measurements")
                .with(jwt())
                .queryParam("startRow", "-1")
                .queryParam("endRow", "20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_400_when_end_row_is_negative() throws Exception {
    // when / then
    mockMvc
        .perform(
            get("/api/measurements")
                .with(jwt())
                .queryParam("startRow", "0")
                .queryParam("endRow", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_400_when_start_row_is_missing() throws Exception {
    // when / then
    mockMvc
        .perform(get("/api/measurements").with(jwt()).queryParam("endRow", "20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_400_when_end_row_is_missing() throws Exception {
    // when / then
    mockMvc
        .perform(get("/api/measurements").with(jwt()).queryParam("startRow", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.params.errorId").exists());
  }

  @Test
  void should_return_401_when_not_authenticated_for_measurements_list() throws Exception {
    // when / then: no .with(jwt()) -> the request carries no credentials at all
    mockMvc
        .perform(get("/api/measurements").queryParam("startRow", "0").queryParam("endRow", "20"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void should_return_400_when_paged_request_parser_rejects_the_request() throws Exception {
    // given: e.g. an unknown sort/filter colId - mirrors GlobalErrorControllerAdvice's handling
    // of InvalidPagedRequestException
    given(pagedRequestParser.parse(any()))
        .willThrow(new InvalidPagedRequestException("Unknown sortable/filterable column: bogus"));

    // when / then
    mockMvc
        .perform(
            get("/api/measurements")
                .with(jwt())
                .queryParam("startRow", "0")
                .queryParam("endRow", "20")
                .queryParam(
                    "filterModel",
                    "{\"bogus\":{\"filterType\":\"text\",\"type\":\"contains\",\"filter\":\"x\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageKey").value("error.paging.invalid"));
  }
}
