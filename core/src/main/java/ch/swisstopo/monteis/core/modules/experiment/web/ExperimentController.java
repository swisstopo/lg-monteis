package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequestParser;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.RawPagedRequest;
import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
  private final ExperimentService service;
  private final ExperimentWebMapper mapper;
  private final Clock clock;
  private final PagedRequestParser pagedRequestParser;

  public ExperimentController(
      ExperimentService service,
      ExperimentWebMapper mapper,
      Clock clock,
      PagedRequestParser pagedRequestParser) {
    this.service = service;
    this.mapper = mapper;
    this.clock = clock;
    this.pagedRequestParser = pagedRequestParser;
  }

  @Operation(summary = "Get a experiment by id", description = "Retrieves a experiment by id")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved formulas")
  @GetMapping(path = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentResponseDto> getExperiment(@PathVariable @Positive Long id) {
    LocalDate today = LocalDate.now(clock);
    return ResponseEntity.ok(mapper.toDto(service.getById(id), today));
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

    Experiment createdExperiment = service.createExperiment(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(createdExperiment, today));
  }

  @Operation(
      summary = "Update an existing experiment",
      description =
          "Updates a experiment's mutable fields. Requires the correct ID and the current version"
              + " number for optimistic locking.")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Experiment successfully updated")})
  @PutMapping(
      path = "{id}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentResponseDto> updateExperiment(
      @PathVariable @Positive Long id,
      @Validated(Update.class) @RequestBody WriteExperimentDto dto) {
    if (!id.equals(dto.id())) {
      throw new ObjectBusinessValidationException(
          "id.validation.mismatch", Map.of("pathId", id, "id", dto.id()));
    }
    LocalDate today = LocalDate.now(clock);

    Experiment updated = service.updateExperiment(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.OK).body(mapper.toDto(updated, today));
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
    RawPagedRequest raw = new RawPagedRequest(startRow, endRow, sortModel, filterModel);
    return service.getExperiments(pagedRequestParser.parse(raw));
  }
}
