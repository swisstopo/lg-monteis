package ch.swisstopo.monteis.core.modules.notification.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.modules.notification.service.RecipientService;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.RecipientsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecipientController {

  private final RecipientService service;
  private final RecipientWebMapper mapper;

  public RecipientController(RecipientService service, RecipientWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Get the Recipients of an experiment",
      description =
          "The users who get the alarms of the experiment, with their contact details, sorted by"
              + " name. The PIs until the list is saved for the first time, the saved list after"
              + " that. Saved users who lost access to the experiment are left out.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved recipients")
  @ApiResponse(responseCode = "403", description = "The caller may not write the experiment")
  @ApiResponse(responseCode = "404", description = "The experiment does not exist")
  @GetMapping(path = ApiPaths.EXPERIMENT_RECIPIENTS, produces = MediaType.APPLICATION_JSON_VALUE)
  public RecipientsResponseDto findRecipientsOf(@PathVariable(ApiPaths.EXPERIMENT_ID) UUID id) {
    return mapper.toDto(service.findRecipientsOf(id));
  }
}
