package ch.swisstopo.monteis.core.modules.organisation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swisstopo.monteis.core.infrastructure.exception.InvalidPagedRequestException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.itconfig.ControllerTest;
import ch.swisstopo.monteis.core.itconfig.PrivilegeLevel;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.query.OrganisationCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.organisation.service.OrganisationService;
import ch.swisstopo.monteis.core.modules.organisation.web.dto.outbound.OrganisationResponseDto;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ControllerTest(OrganisationController.class)
class OrganisationControllerTest {

  private static final UUID ORGANISATION_ID =
      UUID.fromString("0198f3a0-0000-7000-8000-00000000000a");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrganisationService service;
  @MockitoBean private OrganisationWebMapper mapper;
  @MockitoBean private PagedRequestParser pagedRequestParser;
  @MockitoBean private OrganisationCsvExportQueryRepository csvExportQueryRepository;

  @Test
  void should_return_a_page_of_organisations() throws Exception {
    Organisation organisation = new Organisation(ORGANISATION_ID, "Swisstopo", null);
    PagedResult<Organisation> domainResult = new PagedResult<>(List.of(organisation), 1);
    given(pagedRequestParser.parse(any())).willReturn(new PagedRequest(0, 10, null, null));
    given(service.getOrganisations(any())).willReturn(domainResult);
    given(mapper.toPagedDto(domainResult))
        .willReturn(
            new PagedResult<>(
                List.of(new OrganisationResponseDto(ORGANISATION_ID, "Swisstopo", "A comment")),
                1));

    mockMvc
        .perform(
            get("/api/organisations")
                .param("startRow", "0")
                .param("endRow", "10")
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.rows[0].id").value(ORGANISATION_ID.toString()))
        .andExpect(jsonPath("$.rows[0].name").value("Swisstopo"));
  }

  @Test
  void should_return_all_organisations() throws Exception {
    Organisation organisation = new Organisation(ORGANISATION_ID, "Swisstopo", null);
    given(service.findAllOrganisations()).willReturn(List.of(organisation));
    given(mapper.toDto(organisation))
        .willReturn(new OrganisationResponseDto(ORGANISATION_ID, "Swisstopo", "A comment"));

    mockMvc
        .perform(
            get("/api/organisations/all")
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(ORGANISATION_ID.toString()))
        .andExpect(jsonPath("$[0].name").value("Swisstopo"));
  }

  @Test
  void should_return_an_organisation_by_id() throws Exception {
    Organisation organisation = new Organisation(ORGANISATION_ID, "Swisstopo", null);
    given(service.getOrganisation(ORGANISATION_ID)).willReturn(organisation);
    given(mapper.toDto(organisation))
        .willReturn(new OrganisationResponseDto(ORGANISATION_ID, "Swisstopo", "A comment"));

    mockMvc
        .perform(
            get("/api/organisations/{id}", ORGANISATION_ID)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ORGANISATION_ID.toString()))
        .andExpect(jsonPath("$.name").value("Swisstopo"));
  }

  @Test
  void should_answer_404_for_an_unknown_organisation_id() throws Exception {
    given(service.getOrganisation(ORGANISATION_ID))
        .willThrow(new ObjectNotFoundException(Organisation.class));

    mockMvc
        .perform(
            get("/api/organisations/{id}", ORGANISATION_ID)
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isNotFound());
  }

  @Test
  void should_stream_organisations_as_csv() throws Exception {
    PagedRequest exportRequest = new PagedRequest(0, 50000, List.of(), Map.of());
    given(pagedRequestParser.parseForExport(any())).willReturn(exportRequest);
    willAnswer(
            invocation -> {
              Writer writer = invocation.getArgument(1);
              writer.write("name,id\r\nSwisstopo," + ORGANISATION_ID + "\r\n");
              return null;
            })
        .given(csvExportQueryRepository)
        .streamCsv(eq(exportRequest), any());

    mockMvc
        .perform(
            get("/api/organisations/csv")
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv;charset=UTF-8"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"organisations.csv\""))
        .andExpect(content().string("name,id\r\nSwisstopo," + ORGANISATION_ID + "\r\n"));

    then(csvExportQueryRepository).should().streamCsv(eq(exportRequest), any());
  }

  @Test
  void should_answer_400_when_the_csv_export_rejects_the_filter() throws Exception {
    PagedRequest exportRequest = new PagedRequest(0, 50000, List.of(), Map.of());
    given(pagedRequestParser.parseForExport(any())).willReturn(exportRequest);
    willThrow(new InvalidPagedRequestException("Unknown sortable/filterable column: bogus"))
        .given(csvExportQueryRepository)
        .streamCsv(eq(exportRequest), any());

    mockMvc
        .perform(
            get("/api/organisations/csv")
                .with(authentication(PrivilegeLevel.EXPERIMENT_PI.authentication())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageKey").value("error.paging.invalid"));
  }

  @Test
  void should_create_an_organisation() throws Exception {
    Organisation toCreate = new Organisation("Swisstopo", null);
    Organisation created = new Organisation(ORGANISATION_ID, "Swisstopo", null);
    given(mapper.toDomain(any())).willReturn(toCreate);
    given(service.createOrganisation(toCreate)).willReturn(created);
    given(mapper.toDto(created))
        .willReturn(new OrganisationResponseDto(ORGANISATION_ID, "Swisstopo", "A comment"));

    mockMvc
        .perform(
            post("/api/organisations")
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Swisstopo\",\"comment\":\"A comment\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(ORGANISATION_ID.toString()))
        .andExpect(jsonPath("$.comment").value("A comment"));
  }

  @Test
  void should_reject_a_blank_comment() throws Exception {
    mockMvc
        .perform(
            post("/api/organisations")
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Swisstopo\",\"comment\":\" \"}"))
        .andExpect(status().isBadRequest());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_reject_a_blank_name() throws Exception {
    mockMvc
        .perform(
            post("/api/organisations")
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_not_let_a_non_admin_create_an_organisation() throws Exception {
    mockMvc
        .perform(
            post("/api/organisations")
                .with(csrf())
                .with(authentication(PrivilegeLevel.GLOBAL_EDITOR.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Swisstopo\"}"))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }

  @Test
  void should_update_an_organisation() throws Exception {
    Organisation toUpdate = new Organisation("Renamed", null);
    Organisation updated = new Organisation(ORGANISATION_ID, "Renamed", null);
    given(mapper.toDomain(any())).willReturn(toUpdate);
    given(service.updateOrganisation(toUpdate)).willReturn(updated);
    given(mapper.toDto(updated))
        .willReturn(new OrganisationResponseDto(ORGANISATION_ID, "Renamed", "A comment"));

    mockMvc
        .perform(
            put("/api/organisations/{id}", ORGANISATION_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.MONTEIS_ADMIN.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ORGANISATION_ID.toString()))
        .andExpect(jsonPath("$.name").value("Renamed"));

    then(service).should().updateOrganisation(toUpdate);
    assertThat(toUpdate.getId()).isEqualTo(ORGANISATION_ID);
  }

  @Test
  void should_not_let_a_non_admin_update_an_organisation() throws Exception {
    mockMvc
        .perform(
            put("/api/organisations/{id}", ORGANISATION_ID)
                .with(csrf())
                .with(authentication(PrivilegeLevel.GLOBAL_EDITOR.authentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}"))
        .andExpect(status().isForbidden());

    then(service).shouldHaveNoInteractions();
  }
}
