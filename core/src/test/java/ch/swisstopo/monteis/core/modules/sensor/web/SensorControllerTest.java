package ch.swisstopo.monteis.core.modules.sensor.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.security.SecurityConfig;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorQuery;
import ch.swisstopo.monteis.core.modules.sensor.service.SensorService;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteSensorDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SensorController.class)
@Import(SecurityConfig.class)
class SensorControllerTest {

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @MockitoBean private SensorService service;
  @MockitoBean private SensorQuery queryService;

  @MockitoBean private SensorWebMapper mapper;

  // Only used to satisfy SecurityConfig's oauth2ResourceServer bean requirement in this slice
  // test; requests authenticate via the jwt() post-processor instead of a real decode.
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void should_route_create_sensor_and_verify_output() throws Exception {
    // given: Instantiate DTOs for input and expected output
    WriteSensorDto requestDto = new WriteSensorDto(null, "SENS-01", "Test", 0.0, 100.0, null, null);

    SensorResponseDto expectedResponseDto =
        new SensorResponseDto(1L, "SENS-01", "Test", 0.0, 100.0, null, 1);

    // Strictly mock the domain object
    Sensor mockDomain = mock(Sensor.class);

    given(mapper.toDomain(any(WriteSensorDto.class))).willReturn(mockDomain);
    given(service.createSensor(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(mockDomain)).willReturn(expectedResponseDto);

    // when / then: Perform request and assert the actual JSON fields match our expected output DTO
    mockMvc
        .perform(
            post("/api/sensors")
                .with(jwt().authorities(new SimpleGrantedAuthority("api:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id()))
        .andExpect(jsonPath("$.code").value(expectedResponseDto.code()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteSensorDto.class));
    then(service).should().createSensor(mockDomain);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_route_update_sensor_and_verify_output() throws Exception {
    // given
    WriteSensorDto requestDto =
        new WriteSensorDto(1L, "SENS-01", "Updated Sensor", -10.0, 50.0, null, 1);

    SensorResponseDto expectedResponseDto =
        new SensorResponseDto(1L, "SENS-01", "Updated Sensor", -10.0, 50.0, null, 2);

    Sensor mockDomain = mock(Sensor.class);

    given(mapper.toDomain(any(WriteSensorDto.class))).willReturn(mockDomain);
    given(service.updateSensor(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(mockDomain)).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            put("/api/sensors/1")
                .with(jwt().authorities(new SimpleGrantedAuthority("api:write")))
                .param("id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteSensorDto.class));
    then(service).should().updateSensor(mockDomain);
    then(mapper).should().toDto(mockDomain);
  }

  @Test
  void should_route_find_formulas_and_return_json_array() throws Exception {
    // given

    FormulaResponseDto dto1 = new FormulaResponseDto(1L, "x * 2", 1);
    FormulaResponseDto dto2 = new FormulaResponseDto(2L, "y / 2", 1);

    given(queryService.findAllFormulas()).willReturn(List.of(dto1, dto2));

    // when / then
    mockMvc
        .perform(get("/api/sensors/formulas").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(dto1.id()))
        .andExpect(jsonPath("$[0].expression").value(dto1.expression()))
        .andExpect(jsonPath("$[1].id").value(dto2.id()))
        .andExpect(jsonPath("$[1].expression").value(dto2.expression()));

    then(queryService).should().findAllFormulas();
  }
}
