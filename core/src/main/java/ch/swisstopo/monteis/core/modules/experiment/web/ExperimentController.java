package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQuery;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentService;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
  private final ExperimentQuery queryRepository;
  private final ExperimentService service;
  private final ExperimentWebMapper mapper;

  public ExperimentController(
      ExperimentService service, ExperimentWebMapper mapper, ExperimentQuery queryRepository) {
    this.service = service;
    this.mapper = mapper;
    this.queryRepository = queryRepository;
  }

  @GetMapping(value = "/{id}/details", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentResponseDto> getMetrics(@Positive @PathVariable Long id) {

    ExperimentResponseDto result = queryRepository.getExperimentDetails(id);
    return ResponseEntity.ok(result);
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

    Experiment createdExperiment = service.createExperiment(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(createdExperiment));
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

    Experiment updated = service.updateExperiment(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.OK).body(mapper.toDto(updated));
  }
}
