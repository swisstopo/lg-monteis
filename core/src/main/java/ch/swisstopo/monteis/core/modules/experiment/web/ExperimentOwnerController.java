package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerAssignment;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerQueries;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound.WriteExperimentOwnersDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentOwnerDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExperimentOwnerController {

  private final ExperimentOwnerQueries ownerQueries;
  private final ExperimentOwnerAssignment ownerAssignment;
  private final ExperimentWebMapper mapper;
  private final Clock clock;

  public ExperimentOwnerController(
      ExperimentOwnerQueries ownerQueries,
      ExperimentOwnerAssignment ownerAssignment,
      ExperimentWebMapper mapper,
      Clock clock) {
    this.ownerQueries = ownerQueries;
    this.ownerAssignment = ownerAssignment;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Operation(
      summary = "Get the owners of all readable experiments",
      description =
          "Every owner of an experiment visible to the caller, each once, sorted by name. Feeds"
              + " the owner filter of the experiment table. Empty when Keycloak is unavailable.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved owners")
  @GetMapping(path = ApiPaths.ALL_EXPERIMENT_OWNERS, produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ExperimentOwnerDto> getAssignedOwners() {
    return mapper.toOwnerDtos(ownerQueries.filterableOwners());
  }

  @Operation(
      summary = "Get the users that may own an experiment",
      description = "The PIs of the experiment in Keycloak, i.e. the members of its write group.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved candidates")
  @ApiResponse(responseCode = "503", description = "Keycloak is unavailable")
  @GetMapping(
      path = ApiPaths.EXPERIMENT_OWNER_CANDIDATES,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ExperimentOwnerDto> getOwnerCandidates(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id) {
    return mapper.toOwnerDtos(ownerAssignment.ownerCandidates(id));
  }

  @Operation(
      summary = "Replace the owners of an experiment",
      description = "Sets the owners to exactly the given users, each must be a PI of it.")
  @ApiResponse(responseCode = "200", description = "Owners successfully replaced")
  @ApiResponse(responseCode = "503", description = "Keycloak is unavailable")
  @PutMapping(
      path = ApiPaths.EXPERIMENT_OWNERS,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ExperimentResponseDto> replaceOwners(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id,
      @Valid @RequestBody WriteExperimentOwnersDto dto) {
    Experiment updated = ownerAssignment.replaceOwners(id, Set.copyOf(dto.ownerIds()));
    return ResponseEntity.ok(mapper.toDto(ownerQueries.withOwners(updated), LocalDate.now(clock)));
  }
}
