package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerService;
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

  private final ExperimentOwnerService ownerService;
  private final ExperimentWebMapper mapper;
  private final Clock clock;

  public ExperimentOwnerController(
      ExperimentOwnerService ownerService, ExperimentWebMapper mapper, Clock clock) {
    this.ownerService = ownerService;
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
    return mapper.toOwnerDtos(ownerService.filterableOwners());
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
    return mapper.toOwnerDtos(ownerService.candidates(id));
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
    Experiment updated = ownerService.replaceOwners(id, Set.copyOf(dto.ownerIds()));
    return ResponseEntity.ok(
        mapper.toDto(updated, ownerService.ownersOf(updated), LocalDate.now(clock)));
  }
}
