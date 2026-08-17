package ch.swisstopo.monteis.core.modules.experiment.web;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.WRITE_AUTHORITY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(ExperimentController.class)
class ExperimentControllerTest {

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @MockitoBean private ExperimentService service;

  @MockitoBean private ExperimentWebMapper mapper;
  @MockitoBean private PagedRequestParser pagedRequestParser;

  @MockitoBean private Clock clock;

  @BeforeEach
  void setUpClock() {
    // Set up LocalDate.now(clock) to be the exact date
    Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"));

    given(clock.instant()).willReturn(fixedClock.instant());
    given(clock.getZone()).willReturn(fixedClock.getZone());
  }

  private final LocalDate referenceToday = LocalDate.of(2024, Month.JUNE, 15);

  @Test
  void should_route_get_experiment_and_verify_output() throws Exception {
    // given
    Experiment expectedExperiment =
        new Experiment(
            1L,
            "EXP-01",
            new Period(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            "A test experiment",
            2,
            1);

    ExperimentResponseDto expectedResponseDto =
        new ExperimentResponseDto(
            1L,
            "EXP-01",
            "A test experiment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            Status.ACTIVE,
            0,
            1);

    expectedExperiment.getStatus(referenceToday);

    given(service.getById(1L)).willReturn(expectedExperiment);
    given(mapper.toDto(eq(expectedExperiment), any(LocalDate.class)))
        .willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(get("/api/experiments/1").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.comment").value(expectedResponseDto.comment()))
        .andExpect(jsonPath("$.status").value(expectedResponseDto.status().name()));

    then(service).should().getById(1L);
    then(mapper).should().toDto(eq(expectedExperiment), any(LocalDate.class));
  }

  //  @Test
  //  void should_route_get_experiments_and_return_paged_result() throws Exception {
  //    // given
  //    ExperimentResponseDto dto1 =
  //        new ExperimentResponseDto(
  //            1L,
  //            "EXP-01",
  //            "A test experiment",
  //            new PeriodDto(
  //                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
  //            Status.ACTIVE,
  //            2,
  //            1);
  //
  //    given(pagedRequestParser.parse(any()))
  //        .willReturn(
  //            new ch.swisstopo.monteis.core.infrastructure.query.PagedRequest(
  //                0, 20, java.util.List.of(), java.util.Map.of()));
  //    given(queryService.getExperiments(any()))
  //        .willReturn(
  //            new ch.swisstopo.monteis.core.infrastructure.query.PagedResult<>(
  //                java.util.List.of(dto1), 1));
  //
  //    // when / then
  //    mockMvc
  //        .perform(
  //            get("/api/experiments")
  //                .queryParam("startRow", "0")
  //                .queryParam("endRow", "20")
  //                .with(jwt())
  //                .contentType(MediaType.APPLICATION_JSON))
  //        .andExpect(status().isOk())
  //        .andExpect(jsonPath("$.totalCount").value(1))
  //        .andExpect(jsonPath("$.rows[0].id").value(dto1.id()))
  //        .andExpect(jsonPath("$.rows[0].name").value(dto1.name()))
  //        .andExpect(jsonPath("$.rows[0].comment").value(dto1.comment()))
  //        .andExpect(jsonPath("$.rows[0].status").value(dto1.status().name()));
  //
  //    // Verify the read flow bypasses the service/mapper entirely
  //    then(queryService).should().getExperiments(any());
  //    then(service).shouldHaveNoInteractions();
  //    then(mapper).shouldHaveNoInteractions();
  //  }

  @Test
  void should_route_create_experiment_and_verify_output() throws Exception {
    // given
    WriteExperimentDto requestDto =
        new WriteExperimentDto(
            null,
            "EXP-01",
            "A test experiment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            null);

    ExperimentResponseDto expectedResponseDto =
        new ExperimentResponseDto(
            1L,
            "EXP-01",
            "A test experiment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            Status.ACTIVE,
            0,
            1);

    Experiment mockDomain = mock(Experiment.class);

    given(mapper.toDomain(any(WriteExperimentDto.class))).willReturn(mockDomain);
    given(service.createExperiment(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(eq(mockDomain), any(LocalDate.class))).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            post("/api/experiments")
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(
            jsonPath("$.period.start").value(expectedResponseDto.period().start().toString()))
        .andExpect(jsonPath("$.period.end").value(expectedResponseDto.period().end().toString()))
        .andExpect(jsonPath("$.comment").value(expectedResponseDto.comment()))
        .andExpect(jsonPath("$.status").value(expectedResponseDto.status().name()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteExperimentDto.class));
    then(service).should().createExperiment(mockDomain);
    then(mapper).should().toDto(eq(mockDomain), any(LocalDate.class));
  }

  @Test
  void should_route_update_experiment_and_verify_output() throws Exception {
    // given
    WriteExperimentDto requestDto =
        new WriteExperimentDto(
            1L,
            "EXP-01-UPDATED",
            "Updated comment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            1);

    ExperimentResponseDto expectedResponseDto =
        new ExperimentResponseDto(
            1L,
            "EXP-01-UPDATED",
            "Updated comment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            Status.ACTIVE,
            3,
            2);

    Experiment mockDomain = mock(Experiment.class);

    given(mapper.toDomain(any(WriteExperimentDto.class))).willReturn(mockDomain);
    given(service.updateExperiment(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(eq(mockDomain), any(LocalDate.class))).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            put("/api/experiments/1")
                .with(jwt().authorities(new SimpleGrantedAuthority(WRITE_AUTHORITY)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.comment").value(expectedResponseDto.comment()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteExperimentDto.class));
    then(service).should().updateExperiment(mockDomain);
    then(mapper).should().toDto(eq(mockDomain), any(LocalDate.class));
  }

  @Test
  void should_reject_update_when_path_id_does_not_match_body_id() throws Exception {
    // given: path id (1) and body id (2) disagree
    WriteExperimentDto requestDto =
        new WriteExperimentDto(
            2L,
            "EXP-01",
            "A test experiment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            1);

    // when / then
    mockMvc
        .perform(
            put("/api/experiments/1")
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
}
