package ch.swisstopo.monteis.core.modules.organisation.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.infrastructure.csv.CsvDownload;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawExportRequest;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.query.OrganisationCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.organisation.service.OrganisationService;
import ch.swisstopo.monteis.core.modules.organisation.web.dto.inbound.WriteOrganisationDto;
import ch.swisstopo.monteis.core.modules.organisation.web.dto.outbound.OrganisationResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping(ApiPaths.ORGANISATIONS)
public class OrganisationController {
  private final OrganisationService service;
  private final OrganisationWebMapper mapper;
  private final PagedRequestParser pagedRequestParser;
  private final OrganisationCsvExportQueryRepository csvExportQueryRepository;

  public OrganisationController(
      OrganisationService service,
      OrganisationWebMapper mapper,
      PagedRequestParser pagedRequestParser,
      OrganisationCsvExportQueryRepository csvExportQueryRepository) {
    this.service = service;
    this.mapper = mapper;
    this.pagedRequestParser = pagedRequestParser;
    this.csvExportQueryRepository = csvExportQueryRepository;
  }

  @Operation(
      summary = "Get all organisations",
      description =
          "Retrieves an unpaged list of all organisations, sorted alphabetically by name."
              + " Intended for lightweight lookups (e.g. a dropdown).")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved organisations")
  @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<OrganisationResponseDto>> getAllOrganisations() {
    return ResponseEntity.ok(service.findAllOrganisations().stream().map(mapper::toDto).toList());
  }

  @Operation(
      summary = "Get organisations",
      description = "Retrieves a page of organisations with optional sorting/filtering.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved organisations")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public PagedResult<OrganisationResponseDto> getOrganisations(
      @RequestParam @Min(0) int startRow,
      @RequestParam @Min(0) int endRow,
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    RawPagedRequest raw = new RawPagedRequest(startRow, endRow, sortModel, filterModel);
    return mapper.toPagedDto(service.getOrganisations(pagedRequestParser.parse(raw)));
  }

  @Operation(
      summary = "Download organisations as CSV",
      description =
          "Streams all organisations matching the optional sorting/filtering as a CSV file, capped"
              + " at a server-configured maximum row count.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed organisations as CSV",
      content =
          @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
  @GetMapping(value = "/csv", produces = "text/csv")
  public ResponseEntity<StreamingResponseBody> getOrganisationsCsv(
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    PagedRequest exportRequest =
        pagedRequestParser.parseForExport(new RawExportRequest(sortModel, filterModel));
    return CsvDownload.attachment(
        "organisations.csv", writer -> csvExportQueryRepository.streamCsv(exportRequest, writer));
  }

  @Operation(summary = "Get an organisation by id", description = "Retrieves an organisation by id")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved organisation")
  @GetMapping(path = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OrganisationResponseDto> getOrganisation(@PathVariable UUID id) {
    return ResponseEntity.ok(mapper.toDto(service.getOrganisation(id)));
  }

  @Operation(
      summary = "Create a new organisation",
      description = "Adds an organisation to the list. The name must be unique, ignoring case.")
  @ApiResponse(responseCode = "201", description = "Organisation successfully created")
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OrganisationResponseDto> createOrganisation(
      @Valid @RequestBody WriteOrganisationDto dto) {
    Organisation created = service.createOrganisation(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(created));
  }

  @Operation(
      summary = "Update an organisation",
      description =
          "Changes the name and comment of the organisation. The name must be unique, ignoring"
              + " case.")
  @ApiResponse(responseCode = "200", description = "Organisation successfully updated")
  @PutMapping(
      path = "{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OrganisationResponseDto> updateOrganisation(
      @PathVariable UUID id, @Valid @RequestBody WriteOrganisationDto dto) {
    Organisation organisation = mapper.toDomain(dto);
    organisation.setId(id);
    return ResponseEntity.ok(mapper.toDto(service.updateOrganisation(organisation)));
  }
}
