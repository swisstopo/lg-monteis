package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.infrastructure.csv.CsvDownload;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawExportRequest;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.infrastructure.security.Capabilities;
import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerService;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping(ApiPaths.EXPERIMENTS)
public class ExperimentController {
  private final ExperimentService experimentService;
  private final ExperimentOwnerService ownerService;
  private final ExperimentWebMapper mapper;
  private final Clock clock;
  private final PagedRequestParser pagedRequestParser;
  private final ExperimentCsvExportQueryRepository csvExportQueryRepository;

  public ExperimentController(
      ExperimentService experimentService,
      ExperimentOwnerService ownerService,
      ExperimentWebMapper mapper,
      Clock clock,
      PagedRequestParser pagedRequestParser,
      ExperimentCsvExportQueryRepository csvExportQueryRepository) {
    this.experimentService = experimentService;
    this.ownerService = ownerService;
    this.mapper = mapper;
    this.clock = clock;
    this.pagedRequestParser = pagedRequestParser;
    this.csvExportQueryRepository = csvExportQueryRepository;
  }

  @Operation(summary = "Get a experiment by id", description = "Retrieves a experiment by id")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved formulas")
  @GetMapping(path = ApiPaths.EXPERIMENT_ID_SEGMENT, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentResponseDto> getExperiment(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id) {
    LocalDate today = LocalDate.now(clock);
    Experiment experiment = experimentService.getById(id);
    return ResponseEntity.ok(
        mapper.toDto(experiment, ownerService.findOwnersOf(experiment), today));
  }

  @Operation(
      summary = "Create a new experiment",
      description =
          "Creates a new experiment in the system. The 'code' must be unique across all"
              + " experiments.")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "201", description = "Experiment successfully created")})
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentResponseDto> createExperiment(
      @Validated(Create.class) @RequestBody WriteExperimentDto dto) {
    LocalDate today = LocalDate.now(clock);

    Experiment createdExperiment = experimentService.createExperiment(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toDto(createdExperiment, ownerService.findOwnersOf(createdExperiment), today));
  }

  @Operation(
      summary = "Update an existing experiment",
      description =
          "Updates a experiment's mutable fields. Requires the correct ID and the current version"
              + " number for optimistic locking.")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Experiment successfully updated")})
  @PutMapping(
      path = ApiPaths.EXPERIMENT_ID_SEGMENT,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentResponseDto> updateExperiment(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id,
      @Validated(Update.class) @RequestBody WriteExperimentDto dto,
      Authentication authentication) {
    if (!id.equals(dto.id())) {
      throw new ObjectBusinessValidationException(
          "id.validation.mismatch", Map.of("pathId", id, "id", dto.id()));
    }
    LocalDate today = LocalDate.now(clock);

    Experiment updated = update(dto, authentication);
    return ResponseEntity.status(HttpStatus.OK)
        .body(mapper.toDto(updated, ownerService.findOwnersOf(updated), today));
  }

  // the filter chain lets every writer of the experiment through, it can't see the owners in the
  // body
  private Experiment update(WriteExperimentDto dto, Authentication authentication) {
    Experiment experiment = mapper.toDomain(dto);
    if (dto.ownerIds() == null) {
      return experimentService.updateExperiment(experiment);
    }
    // gets refactored in PR 258
    if (!Capabilities.of(authentication).canManageExperimentOwners()) {
      throw new AccessDeniedException("Only admins may change the owners of an experiment");
    }
    return experimentService.updateExperimentAndOwners(experiment, Set.copyOf(dto.ownerIds()));
  }

  @Operation(
      summary = "Get all experiments",
      description =
          "Retrieves an unpaged list of all experiments visible to the current user, sorted"
              + " alphabetically by name. Intended for lightweight lookups (e.g. an autocomplete"
              + " when picking a sensor's main experiment).")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved experiments")
  @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ExperimentResponseDto>> getAllExperiments() {
    LocalDate today = LocalDate.now(clock);
    List<Experiment> experiments = experimentService.findAllExperiments();
    return ResponseEntity.ok(
        mapper.toDtos(experiments, ownerService.findOwnersByExperiment(experiments), today));
  }

  @Operation(
      summary = "Get experiments",
      description = "Retrieves a page of experiments with optional sorting/filtering.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved experiments")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public PagedResult<ExperimentResponseDto> getExperiments(
      @RequestParam @Min(0) int startRow,
      @RequestParam @Min(0) int endRow,
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    LocalDate today = LocalDate.now(clock);

    RawPagedRequest raw = new RawPagedRequest(startRow, endRow, sortModel, filterModel);
    PagedResult<Experiment> domainResult =
        experimentService.getExperiments(pagedRequestParser.parse(raw));
    return mapper.toPagedDto(
        domainResult, ownerService.findOwnersByExperiment(domainResult.rows()), today);
  }

  @Operation(
      summary = "Download experiments as CSV",
      description =
          "Streams all experiments matching the optional sorting/filtering as a CSV file, capped"
              + " at a server-configured maximum row count.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed experiments as CSV",
      content =
          @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
  @GetMapping(value = "/csv", produces = "text/csv")
  public ResponseEntity<StreamingResponseBody> getExperimentsCsv(
      @RequestParam(required = false) String sortModel,
      @RequestParam(required = false) String filterModel) {
    PagedRequest exportRequest =
        pagedRequestParser.parseForExport(new RawExportRequest(sortModel, filterModel));
    Map<UUID, VisibleOwners> owners = ownerService.ownersByExperimentForExport();
    return CsvDownload.attachment(
        "experiments.csv",
        writer -> csvExportQueryRepository.streamCsv(exportRequest, writer, owners));
  }
}
