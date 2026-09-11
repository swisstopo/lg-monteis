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
import ch.swisstopo.monteis.core.modules.sensor.domain.DAS;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorParameterDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorTypeDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterResponseDto;
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
  private static final UUID PARAMETER_ID = UUID.fromString("20000000-0000-0000-0000-000000000301");
  private static final UUID TYPE_ID = UUID.fromString("20000000-0000-0000-0000-000000000101");
  private static final UUID OTHER_TYPE_ID = UUID.fromString("20000000-0000-0000-0000-000000000102");
  private static final UUID FORMULA_ID = UUID.fromString("20000000-0000-0000-0000-000000000201");
  private static final UUID OTHER_FORMULA_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000202");

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @MockitoBean private SensorService service;

  @MockitoBean private SensorWebMapper mapper;
  @MockitoBean private PagedRequestParser pagedRequestParser;

  @Test
  void should_route_get_sensor_and_verify_output() throws Exception {
    // given
    SensorResponseDto expectedResponseDto = defaultResponseDto(SENSOR_ID, "Test", 1);
    Sensor mockDomain = mock(Sensor.class);

    given(service.getSensor(SENSOR_ID)).willReturn(mockDomain);
    given(mapper.toDto(mockDomain)).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            get("/api/sensors/{id}", SENSOR_ID).with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
        .andExpect(jsonPath("$.dasSensorAlias").value(expectedResponseDto.dasSensorAlias()))
        .andExpect(jsonPath("$.das").value(expectedResponseDto.das().name()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(
            jsonPath("$.parameters[0].type.name")
                .value(expectedResponseDto.parameters().getFirst().type().name()));

    then(service).should().getSensor(SENSOR_ID);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_route_get_sensors_and_return_paged_result() throws Exception {
    // given
    SensorResponseDto dto1 = defaultResponseDto(SENSOR_ID, "Test 1", 1);

    Sensor mockDomain = mock(Sensor.class);

    given(pagedRequestParser.parse(any())).willReturn(new PagedRequest(0, 20, List.of(), Map.of()));
    PagedResult<Sensor> sensorPagedResult = new PagedResult<>(List.of(mockDomain), 1);
    given(service.getSensors(any())).willReturn(sensorPagedResult);
    given(mapper.toPagedDto(sensorPagedResult)).willReturn(new PagedResult<>(List.of(dto1), 1));

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
        .andExpect(jsonPath("$.rows[0].dasSensorAlias").value(dto1.dasSensorAlias()))
        .andExpect(
            jsonPath("$.rows[0].parameters[0].type.name")
                .value(dto1.parameters().getFirst().type().name()));

    then(service).should().getSensors(any());
    then(mapper).should().toPagedDto(sensorPagedResult);
  }

  @Test
  void should_route_create_sensor_and_verify_output() throws Exception {
    // given: Instantiate DTOs for input and expected output
    WriteSensorDto requestDto = defaultWriteDto(null, null);
    SensorResponseDto expectedResponseDto = defaultResponseDto(SENSOR_ID, "Test", 1);

    // Strictly mock the domain object
    Sensor mockDomain = mock(Sensor.class);

    given(mapper.toDomain(any(WriteSensorDto.class))).willReturn(mockDomain);
    given(service.createSensor(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(mockDomain)).willReturn(expectedResponseDto);

    // when / then: Perform request and assert the actual JSON fields match our expected output
    // DTO
    mockMvc
        .perform(
            post("/api/sensors")
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
        .andExpect(jsonPath("$.dasSensorAlias").value(expectedResponseDto.dasSensorAlias()))
        .andExpect(jsonPath("$.das").value(expectedResponseDto.das().name()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(
            jsonPath("$.parameters[0].unit")
                .value(expectedResponseDto.parameters().getFirst().unit().name()))
        .andExpect(
            jsonPath("$.parameters[0].type.name")
                .value(expectedResponseDto.parameters().getFirst().type().name()))
        .andExpect(jsonPath("$.coordinates.x").value(expectedResponseDto.coordinates().x()))
        .andExpect(jsonPath("$.coordinates.y").value(expectedResponseDto.coordinates().y()))
        .andExpect(jsonPath("$.coordinates.z").value(expectedResponseDto.coordinates().z()))
        .andExpect(
            jsonPath("$.parameters[0].alarmLimits.lower")
                .value(expectedResponseDto.parameters().getFirst().alarmLimits().lower()))
        .andExpect(
            jsonPath("$.parameters[0].alarmLimits.upper")
                .value(expectedResponseDto.parameters().getFirst().alarmLimits().upper()))
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
    WriteSensorParameterDto parameterDto =
        new WriteSensorParameterDto(
            null,
            "Temperature",
            "TEMP-1",
            Unit.METER,
            new WriteSensorTypeDto("Other"),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            new WriteFormulaDto("x * 2"),
            null);
    WriteSensorDto requestDto =
        new WriteSensorDto(
            null,
            "SENS-02",
            "Formula",
            DAS.SOL_EXPERTS,
            null,
            null,
            null,
            new CoordinatesDto(0, 0, 0),
            true,
            null,
            List.of(parameterDto));

    SensorParameterResponseDto expectedParameterDto =
        new SensorParameterResponseDto(
            PARAMETER_ID,
            "Temperature",
            "TEMP-1",
            new SensorTypeResponseDto(TYPE_ID, "Other", 1),
            Unit.METER,
            new FormulaResponseDto(FORMULA_ID, "x * 2", 1),
            new AlarmLimitsDto(0.0, 100.0),
            true,
            null);
    SensorResponseDto expectedResponseDto =
        new SensorResponseDto(
            SENSOR_ID,
            "SENS-02",
            "Formula",
            DAS.SOL_EXPERTS,
            null,
            null,
            new CoordinatesDto(0, 0, 0),
            true,
            null,
            1,
            List.of(expectedParameterDto));

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
        .andExpect(
            jsonPath("$.parameters[0].formula.id")
                .value(expectedParameterDto.formula().id().toString()))
        .andExpect(
            jsonPath("$.parameters[0].formula.expression")
                .value(expectedParameterDto.formula().expression()))
        .andExpect(
            jsonPath("$.parameters[0].formula.version")
                .value(expectedParameterDto.formula().version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteSensorDto.class));
    then(service).should().createSensor(mockDomain);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_route_update_sensor_and_verify_output() throws Exception {
    // given
    WriteSensorDto requestDto = defaultWriteDto(SENSOR_ID, 1);
    SensorResponseDto expectedResponseDto = defaultResponseDto(SENSOR_ID, "Updated", 2);

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
        .andExpect(jsonPath("$.dasSensorAlias").value(expectedResponseDto.dasSensorAlias()))
        .andExpect(
            jsonPath("$.parameters[0].unit")
                .value(expectedResponseDto.parameters().getFirst().unit().name()))
        .andExpect(
            jsonPath("$.parameters[0].type.name")
                .value(expectedResponseDto.parameters().getFirst().type().name()))
        .andExpect(jsonPath("$.coordinates.x").value(expectedResponseDto.coordinates().x()))
        .andExpect(jsonPath("$.coordinates.y").value(expectedResponseDto.coordinates().y()))
        .andExpect(jsonPath("$.coordinates.z").value(expectedResponseDto.coordinates().z()))
        .andExpect(
            jsonPath("$.parameters[0].alarmLimits.lower")
                .value(expectedResponseDto.parameters().getFirst().alarmLimits().lower()))
        .andExpect(
            jsonPath("$.parameters[0].alarmLimits.upper")
                .value(expectedResponseDto.parameters().getFirst().alarmLimits().upper()))
        .andExpect(jsonPath("$.active").value(expectedResponseDto.active()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteSensorDto.class));
    then(service).should().updateSensor(mockDomain);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_reject_update_when_path_id_does_not_match_body_id() throws Exception {
    // given: path id (SENSOR_ID) and body id (OTHER_SENSOR_ID) disagree
    WriteSensorDto requestDto = defaultWriteDto(OTHER_SENSOR_ID, 1);

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
    Formula formula1 = Formula.builder().id(FORMULA_ID).expression("x * 2").version(1).build();
    Formula formula2 =
        Formula.builder().id(OTHER_FORMULA_ID).expression("x / 2").version(1).build();
    FormulaResponseDto dto1 = new FormulaResponseDto(FORMULA_ID, "x * 2", 1);
    FormulaResponseDto dto2 = new FormulaResponseDto(OTHER_FORMULA_ID, "x / 2", 1);

    given(service.findAllFormulas()).willReturn(List.of(formula1, formula2));
    given(mapper.toDto(formula1)).willReturn(dto1);
    given(mapper.toDto(formula2)).willReturn(dto2);

    // when / then
    mockMvc
        .perform(get("/api/sensors/formulas").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(dto1.id().toString()))
        .andExpect(jsonPath("$[0].expression").value(dto1.expression()))
        .andExpect(jsonPath("$[1].id").value(dto2.id().toString()))
        .andExpect(jsonPath("$[1].expression").value(dto2.expression()));

    then(service).should().findAllFormulas();
  }

  @Test
  void should_route_find_types_and_return_json_array() throws Exception {
    // given
    SensorType type1 = new SensorType(TYPE_ID, "Other", 1);
    SensorType type2 = new SensorType(OTHER_TYPE_ID, "Temperature", 1);
    SensorTypeResponseDto dto1 = new SensorTypeResponseDto(TYPE_ID, "Other", 1);
    SensorTypeResponseDto dto2 = new SensorTypeResponseDto(OTHER_TYPE_ID, "Temperature", 1);

    given(service.findAllTypes()).willReturn(List.of(type1, type2));
    given(mapper.toDto(type1)).willReturn(dto1);
    given(mapper.toDto(type2)).willReturn(dto2);

    // when / then
    mockMvc
        .perform(get("/api/sensors/types").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(dto1.id().toString()))
        .andExpect(jsonPath("$[0].name").value(dto1.name()))
        .andExpect(jsonPath("$[1].id").value(dto2.id().toString()))
        .andExpect(jsonPath("$[1].name").value(dto2.name()));

    then(service).should().findAllTypes();
  }

  private WriteSensorParameterDto defaultWriteParameterDto() {
    return new WriteSensorParameterDto(
        null,
        "Temperature",
        "TEMP-1",
        Unit.METER,
        new WriteSensorTypeDto("Other"),
        new AlarmLimitsDto(0.0, 100.0),
        true,
        null,
        null);
  }

  private WriteSensorDto defaultWriteDto(UUID id, Integer version) {
    return new WriteSensorDto(
        id,
        "Test",
        "SENS-01",
        DAS.SOL_EXPERTS,
        null,
        null,
        null,
        new CoordinatesDto(0, 0, 0),
        true,
        version,
        List.of(defaultWriteParameterDto()));
  }

  private SensorParameterResponseDto defaultResponseParameterDto() {
    return new SensorParameterResponseDto(
        PARAMETER_ID,
        "Temperature",
        "TEMP-1",
        new SensorTypeResponseDto(TYPE_ID, "Other", 1),
        Unit.METER,
        null,
        new AlarmLimitsDto(0.0, 100.0),
        true,
        null);
  }

  private SensorResponseDto defaultResponseDto(UUID id, String name, Integer version) {
    return new SensorResponseDto(
        id,
        name,
        "SENS-01",
        DAS.SOL_EXPERTS,
        null,
        null,
        new CoordinatesDto(0, 0, 0),
        true,
        null,
        version,
        List.of(defaultResponseParameterDto()));
  }
}
