package ch.swisstopo.monteis.core.modules.sensor.web;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.WRITE_AUTHORITY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorQuery;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorTypeDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(SensorController.class)
class SensorControllerTest {

  private static final UUID SENSOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_SENSOR_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID TYPE_ID = UUID.fromString("20000000-0000-0000-0000-000000000101");
  private static final UUID OTHER_TYPE_ID = UUID.fromString("20000000-0000-0000-0000-000000000102");
  private static final UUID FORMULA_ID = UUID.fromString("20000000-0000-0000-0000-000000000201");
  private static final UUID OTHER_FORMULA_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000202");

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @MockitoBean private SensorService service;
  @MockitoBean private SensorQuery queryService;

  @MockitoBean private SensorWebMapper mapper;
  @MockitoBean private PagedRequestParser pagedRequestParser;

  @Test
  void should_route_get_sensor_and_verify_output() throws Exception {
    // given
    SensorResponseDto expectedResponseDto =
        new SensorResponseDto(
            SENSOR_ID,
            "SENS-01",
            "Test",
            Unit.METER,
            new SensorTypeResponseDto(TYPE_ID, "Other", 1),
            null,
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            null,
            1);

    given(queryService.getById(SENSOR_ID)).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            get("/api/sensors/{id}", SENSOR_ID).with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
        .andExpect(jsonPath("$.code").value(expectedResponseDto.code()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.type.name").value(expectedResponseDto.type().name()));

    // Verify the read flow bypasses the service/mapper entirely
    then(queryService).should().getById(SENSOR_ID);
    then(service).shouldHaveNoInteractions();
    then(mapper).shouldHaveNoInteractions();
  }

  @Test
  void should_route_get_sensors_and_return_paged_result() throws Exception {
    // given
    SensorResponseDto dto1 =
        new SensorResponseDto(
            SENSOR_ID,
            "SENS-01",
            "Test 1",
            Unit.METER,
            new SensorTypeResponseDto(TYPE_ID, "Other", 1),
            null,
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            null,
            1);

    given(pagedRequestParser.parse(any())).willReturn(new PagedRequest(0, 20, List.of(), Map.of()));
    given(queryService.getSensors(any())).willReturn(new PagedResult<>(List.of(dto1), 1));

    // when / then
    mockMvc
        .perform(
            get("/api/sensors")
                .queryParam("startRow", "0")
                .queryParam("endRow", "20")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.rows[0].id").value(dto1.id().toString()))
        .andExpect(jsonPath("$.rows[0].code").value(dto1.code()))
        .andExpect(jsonPath("$.rows[0].type.name").value(dto1.type().name()));

    // Verify the read flow bypasses the service/mapper entirely
    then(queryService).should().getSensors(any());
    then(service).shouldHaveNoInteractions();
    then(mapper).shouldHaveNoInteractions();
  }

  @Test
  void should_route_create_sensor_and_verify_output() throws Exception {
    // given: Instantiate DTOs for input and expected output
    WriteSensorDto requestDto =
        new WriteSensorDto(
            null,
            "SENS-01",
            "Test",
            null,
            Unit.METER,
            new WriteSensorTypeDto("Other"),
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            null,
            null);

    SensorResponseDto expectedResponseDto =
        new SensorResponseDto(
            SENSOR_ID,
            "SENS-01",
            "Test",
            Unit.METER,
            new SensorTypeResponseDto(TYPE_ID, "Other", 1),
            null,
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            null,
            1);

    // Strictly mock the domain object
    Sensor mockDomain = mock(Sensor.class);

    given(mapper.toDomain(any(WriteSensorDto.class))).willReturn(mockDomain);
    given(service.createSensor(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(mockDomain)).willReturn(expectedResponseDto);

    // when / then: Perform request and assert the actual JSON fields match our expected output DTO
    mockMvc
        .perform(
            post("/api/sensors")
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
        .andExpect(jsonPath("$.code").value(expectedResponseDto.code()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.unit").value(expectedResponseDto.unit().name()))
        .andExpect(jsonPath("$.type.name").value(expectedResponseDto.type().name()))
        .andExpect(jsonPath("$.coordinates.x").value(expectedResponseDto.coordinates().x()))
        .andExpect(jsonPath("$.coordinates.y").value(expectedResponseDto.coordinates().y()))
        .andExpect(jsonPath("$.coordinates.z").value(expectedResponseDto.coordinates().z()))
        .andExpect(jsonPath("$.alarmLimits.lower").value(expectedResponseDto.alarmLimits().lower()))
        .andExpect(jsonPath("$.alarmLimits.upper").value(expectedResponseDto.alarmLimits().upper()))
        .andExpect(jsonPath("$.active").value(expectedResponseDto.active()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteSensorDto.class));
    then(service).should().createSensor(mockDomain);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_route_create_sensor_with_formula_and_verify_output() throws Exception {
    // given: request carries a WriteFormulaDto to exercise the nested formula mapping
    WriteSensorDto requestDto =
        new WriteSensorDto(
            null,
            "SENS-02",
            "Formula",
            null,
            Unit.METER,
            new WriteSensorTypeDto("Other"),
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            new WriteFormulaDto("x * 2"),
            null);

    SensorResponseDto expectedResponseDto =
        new SensorResponseDto(
            SENSOR_ID,
            "SENS-02",
            "Formula",
            Unit.METER,
            new SensorTypeResponseDto(TYPE_ID, "Other", 1),
            null,
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            new FormulaResponseDto(FORMULA_ID, "x * 2", 1),
            1);

    Sensor mockDomain = mock(Sensor.class);

    given(mapper.toDomain(any(WriteSensorDto.class))).willReturn(mockDomain);
    given(service.createSensor(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(mockDomain)).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            post("/api/sensors")
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.formula.id").value(expectedResponseDto.formula().id().toString()))
        .andExpect(
            jsonPath("$.formula.expression").value(expectedResponseDto.formula().expression()))
        .andExpect(jsonPath("$.formula.version").value(expectedResponseDto.formula().version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteSensorDto.class));
    then(service).should().createSensor(mockDomain);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_route_update_sensor_and_verify_output() throws Exception {
    // given
    WriteSensorDto requestDto =
        new WriteSensorDto(
            SENSOR_ID,
            "SENS-01",
            "Updated",
            null,
            Unit.METER,
            new WriteSensorTypeDto("Other"),
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(-10.0, 50.0),
            true,
            null,
            1);

    SensorResponseDto expectedResponseDto =
        new SensorResponseDto(
            SENSOR_ID,
            "SENS-01",
            "Updated",
            Unit.METER,
            new SensorTypeResponseDto(TYPE_ID, "Other", 1),
            null,
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(-10.0, 50.0),
            true,
            null,
            2);

    Sensor mockDomain = mock(Sensor.class);

    given(mapper.toDomain(any(WriteSensorDto.class))).willReturn(mockDomain);
    given(service.updateSensor(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(mockDomain)).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            put("/api/sensors/{id}", SENSOR_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.code").value(expectedResponseDto.code()))
        .andExpect(jsonPath("$.unit").value(expectedResponseDto.unit().name()))
        .andExpect(jsonPath("$.type.name").value(expectedResponseDto.type().name()))
        .andExpect(jsonPath("$.coordinates.x").value(expectedResponseDto.coordinates().x()))
        .andExpect(jsonPath("$.coordinates.y").value(expectedResponseDto.coordinates().y()))
        .andExpect(jsonPath("$.coordinates.z").value(expectedResponseDto.coordinates().z()))
        .andExpect(jsonPath("$.alarmLimits.lower").value(expectedResponseDto.alarmLimits().lower()))
        .andExpect(jsonPath("$.alarmLimits.upper").value(expectedResponseDto.alarmLimits().upper()))
        .andExpect(jsonPath("$.active").value(expectedResponseDto.active()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteSensorDto.class));
    then(service).should().updateSensor(mockDomain);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_reject_update_when_path_id_does_not_match_body_id() throws Exception {
    // given: path id (1) and body id (2) disagree
    WriteSensorDto requestDto =
        new WriteSensorDto(
            OTHER_SENSOR_ID,
            "SENS-01",
            "Updated",
            null,
            Unit.METER,
            new WriteSensorTypeDto("Other"),
            new CoordinatesDto(0, 0, 0),
            new AlarmLimitsDto(-10.0, 50.0),
            true,
            null,
            1);

    // when / then
    mockMvc
        .perform(
            put("/api/sensors/{id}", SENSOR_ID)
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.field").doesNotExist())
        .andExpect(jsonPath("$.messageKey").value("id.validation.mismatch"));

    // Verify the mismatch is caught before any domain/service work happens
    then(mapper).shouldHaveNoInteractions();
    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_route_find_formulas_and_return_json_array() throws Exception {
    // given

    FormulaResponseDto dto1 = new FormulaResponseDto(FORMULA_ID, "x * 2", 1);
    FormulaResponseDto dto2 = new FormulaResponseDto(OTHER_FORMULA_ID, "y / 2", 1);

    given(queryService.findAllFormulas()).willReturn(List.of(dto1, dto2));

    // when / then
    mockMvc
        .perform(get("/api/sensors/formulas").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(dto1.id().toString()))
        .andExpect(jsonPath("$[0].expression").value(dto1.expression()))
        .andExpect(jsonPath("$[1].id").value(dto2.id().toString()))
        .andExpect(jsonPath("$[1].expression").value(dto2.expression()));

    then(queryService).should().findAllFormulas();
  }

  @Test
  void should_route_find_types_and_return_json_array() throws Exception {
    // given
    SensorTypeResponseDto dto1 = new SensorTypeResponseDto(TYPE_ID, "Other", 1);
    SensorTypeResponseDto dto2 = new SensorTypeResponseDto(OTHER_TYPE_ID, "Temperature", 1);

    given(queryService.findAllTypes()).willReturn(List.of(dto1, dto2));

    // when / then
    mockMvc
        .perform(get("/api/sensors/types").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(dto1.id().toString()))
        .andExpect(jsonPath("$[0].name").value(dto1.name()))
        .andExpect(jsonPath("$[1].id").value(dto2.id().toString()))
        .andExpect(jsonPath("$[1].name").value(dto2.name()));

    then(queryService).should().findAllTypes();
  }
}
