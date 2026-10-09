package ch.swisstopo.monteis.core.modules.experiment.web;

import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.ASSIGNED_EXPERIMENT;
import static ch.swisstopo.monteis.core.itconfig.PrivilegeLevel.OTHER_EXPERIMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.OwnersStatus;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerService;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentWithOwners;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentOwnerDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Writer;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(ExperimentController.class)
class ExperimentControllerTest {

  // the experiment the ExperimentPI fixture may edit, and one it may only read
  private static final UUID EXPERIMENT_ID = ASSIGNED_EXPERIMENT;
  private static final UUID OTHER_EXPERIMENT_ID = OTHER_EXPERIMENT;

  private static final String NOT_FOUND_BODY =
      "{\"target\":\"GLOBAL\",\"field\":null,\"actualValue\":null,"
          + "\"messageKey\":\"object.not-found\",\"params\":{}}";
  private static final String ACCESS_DENIED_BODY =
      "{\"target\":\"GLOBAL\",\"field\":null,\"actualValue\":null,"
          + "\"messageKey\":\"access.denied\",\"params\":{}}";

  @Autowired private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @MockitoBean private ExperimentService service;
  @MockitoBean private ExperimentOwnerService ownerService;

  @MockitoBean private ExperimentWebMapper mapper;
  @MockitoBean private PagedRequestParser pagedRequestParser;

  @MockitoBean private Clock clock;

  @MockitoBean private ExperimentCsvExportQueryRepository csvExportQueryRepository;

  @BeforeEach
  void setUpClock() {
    // Set up LocalDate.now(clock) to be the exact date
    Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"));

    given(clock.instant()).willReturn(fixedClock.instant());
    given(clock.getZone()).willReturn(fixedClock.getZone());
  }

  private final LocalDate referenceToday = LocalDate.of(2024, Month.JUNE, 15);

  private static final DirectoryUser OWNER =
      new DirectoryUser(UUID.randomUUID(), "Alice", "Example", "alice@example.test");
  private static final ExperimentOwnerDto OWNER_DTO =
      new ExperimentOwnerDto(OWNER.id(), OWNER.firstName(), OWNER.lastName(), OWNER.email());

  @BeforeEach
  void experimentsComeWithoutOwners() {
    given(ownerService.resolveOwnersOf(any(Experiment.class)))
        .willAnswer(call -> withoutOwners(call.getArgument(0)));
    given(ownerService.resolveOwnersOf(anyList()))
        .willAnswer(
            call ->
                call.<List<Experiment>>getArgument(0).stream()
                    .map(ExperimentControllerTest::withoutOwners)
                    .toList());
  }

  private static ExperimentWithOwners withoutOwners(Experiment experiment) {
    return new ExperimentWithOwners(experiment, VisibleOwners.NONE);
  }

  @Test
  void should_route_get_experiment_and_verify_output() throws Exception {
    // given
    Experiment expectedExperiment =
        new Experiment(
            EXPERIMENT_ID,
            "EXP-01",
            new Period(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            "A test experiment",
            2,
            1,
            Set.of(OWNER.id()));

    ExperimentResponseDto expectedResponseDto =
        new ExperimentResponseDto(
            EXPERIMENT_ID,
            "EXP-01",
            "A test experiment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            Status.ACTIVE,
            0,
            1,
            List.of(OWNER_DTO),
            OwnersStatus.SHOWN);

    expectedExperiment.getStatus(referenceToday);

    given(service.getById(EXPERIMENT_ID)).willReturn(expectedExperiment);
    ExperimentWithOwners withOwners =
        new ExperimentWithOwners(expectedExperiment, VisibleOwners.of(List.of(OWNER)));
    given(ownerService.resolveOwnersOf(expectedExperiment)).willReturn(withOwners);
    given(mapper.toDto(eq(withOwners), any(LocalDate.class))).willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            get("/api/experiments/{id}", EXPERIMENT_ID)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.comment").value(expectedResponseDto.comment()))
        .andExpect(jsonPath("$.status").value(expectedResponseDto.status().name()))
        .andExpect(jsonPath("$.owners[0].id").value(OWNER.id().toString()))
        .andExpect(jsonPath("$.owners[0].email").value(OWNER.email()));

    then(service).should().getById(EXPERIMENT_ID);
    then(mapper).should().toDto(eq(withOwners), any(LocalDate.class));
  }

  @Test
  void should_route_get_experiments_and_return_paged_result() throws Exception {
    // given
    LocalDate startDate = LocalDate.of(2024, Month.JANUARY, 1);
    LocalDate endDate = LocalDate.of(2024, Month.DECEMBER, 31);

    Experiment experiment1 =
        new Experiment(
            EXPERIMENT_ID,
            "EXP-01",
            new Period(startDate, endDate),
            "A test experiment",
            2,
            1,
            Set.of());

    PagedRequest parsedRequest = new PagedRequest(0, 20, List.of(), Map.of());
    PagedResult<Experiment> domainResult = new PagedResult<>(List.of(experiment1), 1);

    ExperimentResponseDto responseDto =
        new ExperimentResponseDto(
            EXPERIMENT_ID,
            "EXP-01",
            "A test experiment",
            new PeriodDto(startDate, endDate),
            Status.HISTORIC, // Example status
            2,
            1,
            List.of(),
            OwnersStatus.SHOWN);
    PagedResult<ExperimentResponseDto> dtoResult = new PagedResult<>(List.of(responseDto), 1);

    given(pagedRequestParser.parse(any())).willReturn(parsedRequest);
    given(service.getExperiments(parsedRequest)).willReturn(domainResult);
    PagedResult<ExperimentWithOwners> withOwners =
        new PagedResult<>(List.of(withoutOwners(experiment1)), 1);
    given(ownerService.resolveOwnersOf(domainResult)).willReturn(withOwners);
    given(mapper.toPagedDto(eq(withOwners), any(LocalDate.class))).willReturn(dtoResult);

    // when / then
    mockMvc
        .perform(
            get("/api/experiments")
                .queryParam("startRow", "0")
                .queryParam("endRow", "20")
                .with(jwt()) // Ensure this matches your security test setup
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.rows[0].id").value(EXPERIMENT_ID.toString()))
        .andExpect(jsonPath("$.rows[0].name").value("EXP-01"))
        .andExpect(jsonPath("$.rows[0].comment").value("A test experiment"))
        .andExpect(jsonPath("$.rows[0].version").value(2))
        .andExpect(jsonPath("$.rows[0].sensorCount").value(1))
        .andExpect(jsonPath("$.rows[0].status").value("HISTORIC"));

    // Verify interaction sequence
    then(pagedRequestParser).should().parse(any());
    then(service).should().getExperiments(parsedRequest);
    then(mapper).should().toPagedDto(eq(withOwners), any(LocalDate.class));

    then(service).shouldHaveNoMoreInteractions();
    then(mapper).shouldHaveNoMoreInteractions();
  }

  @Test
  void should_route_get_experiments_csv_and_stream_csv_content() throws Exception {
    // given
    PagedRequest exportRequest = new PagedRequest(0, 50000, List.of(), Map.of());
    given(pagedRequestParser.parseForExport(any())).willReturn(exportRequest);
    willAnswer(
            invocation -> {
              Writer writer = invocation.getArgument(1);
              writer.write("name,status\r\nEXP-01,ACTIVE\r\n");
              return null;
            })
        .given(csvExportQueryRepository)
        .streamCsv(eq(exportRequest), any(), any());

    // when / then
    mockMvc
        .perform(get("/api/experiments/csv").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv;charset=UTF-8"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"experiments.csv\""))
        .andExpect(content().string("name,status\r\nEXP-01,ACTIVE\r\n"));

    then(pagedRequestParser).should().parseForExport(any());
    then(csvExportQueryRepository).should().streamCsv(eq(exportRequest), any(), any());
  }

  @Test
  void should_return_bad_request_when_csv_export_query_rejects_the_filter() throws Exception {
    // given: the repository throws before writing any bytes (an unknown filter column) - this
    // must still surface as a normal 400 via GlobalErrorControllerAdvice, not a broken/partial
    // 200 response
    PagedRequest exportRequest = new PagedRequest(0, 50000, List.of(), Map.of());
    given(pagedRequestParser.parseForExport(any())).willReturn(exportRequest);
    willThrow(new InvalidPagedRequestException("Unknown sortable/filterable column: bogus"))
        .given(csvExportQueryRepository)
        .streamCsv(eq(exportRequest), any(), any());

    // when / then
    mockMvc
        .perform(get("/api/experiments/csv").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageKey").value("error.paging.invalid"));
  }

  @Test
  void should_route_get_all_experiments_and_return_json_array() throws Exception {
    // given
    LocalDate startDate = LocalDate.of(2024, Month.JANUARY, 1);
    LocalDate endDate = LocalDate.of(2024, Month.DECEMBER, 31);

    Experiment experiment1 =
        new Experiment(
            EXPERIMENT_ID,
            "EXP-01",
            new Period(startDate, endDate),
            "A test experiment",
            2,
            1,
            Set.of());

    ExperimentResponseDto responseDto =
        new ExperimentResponseDto(
            EXPERIMENT_ID,
            "EXP-01",
            "A test experiment",
            new PeriodDto(startDate, endDate),
            Status.HISTORIC,
            2,
            1,
            List.of(),
            OwnersStatus.SHOWN);

    given(service.findAllExperiments()).willReturn(List.of(experiment1));
    given(mapper.toDto(eq(withoutOwners(experiment1)), any(LocalDate.class)))
        .willReturn(responseDto);

    // when / then
    mockMvc
        .perform(get("/api/experiments/all").with(jwt()).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(EXPERIMENT_ID.toString()))
        .andExpect(jsonPath("$[0].name").value("EXP-01"))
        .andExpect(jsonPath("$[0].comment").value("A test experiment"));

    then(service).should().findAllExperiments();
    then(mapper).should().toDto(eq(withoutOwners(experiment1)), any(LocalDate.class));
  }

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
            EXPERIMENT_ID,
            "EXP-01",
            "A test experiment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            Status.ACTIVE,
            0,
            1,
            List.of(),
            OwnersStatus.SHOWN);

    Experiment mockDomain = mock(Experiment.class);

    given(mapper.toDomain(any(WriteExperimentDto.class))).willReturn(mockDomain);
    given(service.createExperiment(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(eq(withoutOwners(mockDomain)), any(LocalDate.class)))
        .willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            post("/api/experiments")
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
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
    then(mapper).should().toDto(eq(withoutOwners(mockDomain)), any(LocalDate.class));
  }

  @Test
  void should_route_update_experiment_and_verify_output() throws Exception {
    // given
    WriteExperimentDto requestDto =
        new WriteExperimentDto(
            EXPERIMENT_ID,
            "EXP-01-UPDATED",
            "Updated comment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            1);

    ExperimentResponseDto expectedResponseDto =
        new ExperimentResponseDto(
            EXPERIMENT_ID,
            "EXP-01-UPDATED",
            "Updated comment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            Status.ACTIVE,
            3,
            2,
            List.of(),
            OwnersStatus.SHOWN);

    Experiment mockDomain = mock(Experiment.class);

    given(mapper.toDomain(any(WriteExperimentDto.class))).willReturn(mockDomain);
    given(service.updateExperiment(mockDomain)).willReturn(mockDomain);
    given(ownerService.dropFormerOwners(mockDomain)).willReturn(mockDomain);
    given(mapper.toDto(eq(withoutOwners(mockDomain)), any(LocalDate.class)))
        .willReturn(expectedResponseDto);

    // when / then
    mockMvc
        .perform(
            put("/api/experiments/{id}", EXPERIMENT_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(expectedResponseDto.id().toString()))
        .andExpect(jsonPath("$.name").value(expectedResponseDto.name()))
        .andExpect(jsonPath("$.comment").value(expectedResponseDto.comment()))
        .andExpect(jsonPath("$.version").value(expectedResponseDto.version()));

    // Verify interaction sequence
    then(mapper).should().toDomain(any(WriteExperimentDto.class));
    then(service).should().updateExperiment(mockDomain);
    then(mapper).should().toDto(eq(withoutOwners(mockDomain)), any(LocalDate.class));
  }

  @Test
  void should_reject_update_when_path_id_does_not_match_body_id() throws Exception {
    // given: path id (1) and body id (2) disagree
    WriteExperimentDto requestDto =
        new WriteExperimentDto(
            OTHER_EXPERIMENT_ID,
            "EXP-01",
            "A test experiment",
            new PeriodDto(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
            1);

    // when / then
    mockMvc
        .perform(
            put("/api/experiments/{id}", EXPERIMENT_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication()))
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
  void should_return_404_object_not_found_when_the_experiment_is_missing_or_hidden()
      throws Exception {
    // given
    UUID hiddenId = UUID.fromString("00000000-0000-7000-8000-000000000302");
    UUID randomId = UUID.randomUUID();
    given(service.getById(any(UUID.class)))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    // when
    String hiddenBody =
        mockMvc
            .perform(
                get("/api/experiments/{id}", hiddenId)
                    .with(csrf())
                    .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
            .andExpect(status().isNotFound())
            .andExpect(content().json(NOT_FOUND_BODY, JsonCompareMode.STRICT))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String randomBody =
        mockMvc
            .perform(
                get("/api/experiments/{id}", randomId)
                    .with(csrf())
                    .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication())))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // then: existence does not leak (NFR1.4)
    assertEquals(randomBody, hiddenBody);
  }

  @Test
  void should_forbid_a_read_only_user_to_update_without_touching_the_service() throws Exception {
    // given: ExperimentUser may read EXPERIMENT_ID but not edit it
    WriteExperimentDto requestDto = updateDto(EXPERIMENT_ID);

    // when / then
    mockMvc
        .perform(
            put("/api/experiments/{id}", EXPERIMENT_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_USER.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
        .andExpect(status().isForbidden())
        .andExpect(content().json(ACCESS_DENIED_BODY, JsonCompareMode.STRICT));

    // the filter chain denied before any UPDATE could be issued (NFR2.1)
    then(service).shouldHaveNoInteractions();
    then(mapper).shouldHaveNoInteractions();
  }

  @Test
  void should_forbid_a_pi_to_update_an_experiment_it_may_only_read() throws Exception {
    mockMvc
        .perform(
            put("/api/experiments/{id}", OTHER_EXPERIMENT_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto(OTHER_EXPERIMENT_ID))))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_let_a_global_editor_update_any_experiment() throws Exception {
    // given
    Experiment mockDomain = mock(Experiment.class);
    given(mapper.toDomain(any(WriteExperimentDto.class))).willReturn(mockDomain);
    given(service.updateExperiment(mockDomain)).willReturn(mockDomain);
    given(ownerService.dropFormerOwners(mockDomain)).willReturn(mockDomain);

    // when / then
    mockMvc
        .perform(
            put("/api/experiments/{id}", OTHER_EXPERIMENT_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.GLOBAL_EDITOR.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto(OTHER_EXPERIMENT_ID))))
        .andExpect(status().isOk());

    then(service).should().updateExperiment(mockDomain);
  }

  @Test
  void should_forbid_everyone_but_admins_to_create_experiments() throws Exception {
    // given
    String body =
        objectMapper.writeValueAsString(
            new WriteExperimentDto(
                null,
                "EXP-01",
                "A test experiment",
                new PeriodDto(
                    LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
                null));

    // when / then
    for (PrivilegeLevel level :
        List.of(
            PrivilegeLevel.BASISROLLE,
            PrivilegeLevel.EXPERIMENT_USER,
            PrivilegeLevel.EXPERIMENT_PI,
            PrivilegeLevel.GLOBAL_EDITOR)) {
      mockMvc
          .perform(
              post("/api/experiments")
                  .with(csrf())
                  .with(authentication(level.authentication()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isForbidden())
          .andExpect(content().json(ACCESS_DENIED_BODY, JsonCompareMode.STRICT));
    }
    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_return_403_access_denied_when_row_level_security_refuses_the_update()
      throws Exception {
    // given: the filter chain allowed it, but the RLS WITH CHECK rejected the row (42501)
    Experiment mockDomain = mock(Experiment.class);
    given(mapper.toDomain(any(WriteExperimentDto.class))).willReturn(mockDomain);
    given(service.updateExperiment(mockDomain))
        .willThrow(new PermissionDeniedDataAccessException("42501", null));

    // when / then: the same body as a filter-chain denial (NFR2.4)
    mockMvc
        .perform(
            put("/api/experiments/{id}", EXPERIMENT_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto(EXPERIMENT_ID))))
        .andExpect(status().isForbidden())
        .andExpect(content().json(ACCESS_DENIED_BODY, JsonCompareMode.STRICT));
  }

  @Test
  void should_return_404_when_the_experiment_to_update_is_missing_or_hidden() throws Exception {
    // given
    Experiment mockDomain = mock(Experiment.class);
    given(mapper.toDomain(any(WriteExperimentDto.class))).willReturn(mockDomain);
    given(service.updateExperiment(mockDomain))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    // when / then: no longer 422 object.deleted (BR4.11)
    mockMvc
        .perform(
            put("/api/experiments/{id}", EXPERIMENT_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto(EXPERIMENT_ID))))
        .andExpect(status().isNotFound())
        .andExpect(content().json(NOT_FOUND_BODY, JsonCompareMode.STRICT));
  }

  private static WriteExperimentDto updateDto(UUID id) {
    return new WriteExperimentDto(
        id,
        "EXP-01-UPDATED",
        "Updated comment",
        new PeriodDto(LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31)),
        1);
  }
}
