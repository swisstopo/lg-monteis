package ch.swisstopo.monteis.core.modules.experiment.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.modules.experiment.service.ExperimentOwnerService;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentOwnerDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExperimentOwnerController {

  private final ExperimentOwnerService ownerService;
  private final ExperimentWebMapper mapper;

  public ExperimentOwnerController(
      ExperimentOwnerService ownerService, ExperimentWebMapper mapper) {
    this.ownerService = ownerService;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Get the owners of all readable experiments",
      description =
          "Every owner of an experiment visible to the caller, each once, sorted by name. Feeds"
              + " the owner filter of the experiment table. Empty when Keycloak is unavailable.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved owners")
  @GetMapping(path = ApiPaths.ALL_EXPERIMENT_OWNERS, produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ExperimentOwnerDto> findAllVisibleOwners() {
    return mapper.toOwnerDtos(ownerService.findAllVisibleOwners());
  }

  @Operation(
      summary = "Get the users that may own an experiment",
      description = "The PIs of the experiment in Keycloak, i.e. the members of its write group.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved candidates")
  @ApiResponse(responseCode = "503", description = "Keycloak is unavailable")
  @GetMapping(
      path = ApiPaths.EXPERIMENT_OWNER_CANDIDATES,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ExperimentOwnerDto> findOwnerCandidatesForExperiment(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id) {
    return mapper.toOwnerDtos(ownerService.findOwnerCandidates(id));
  }
}
