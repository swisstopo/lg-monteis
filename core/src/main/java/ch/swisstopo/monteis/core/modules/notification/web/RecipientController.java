package ch.swisstopo.monteis.core.modules.notification.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUserDto;
import ch.swisstopo.monteis.core.modules.notification.service.RecipientService;
import ch.swisstopo.monteis.core.modules.notification.web.dto.inbound.ReplaceRecipientsDto;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.RecipientsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @Operation(
      summary = "Replace the Recipients of an experiment",
      description =
          "Stores the whole list as sent and answers with the stored Recipients like the GET."
              + " An empty list is valid and means nobody gets the alarms. PIs added to the"
              + " experiment later are not added to a saved list.")
  @ApiResponse(responseCode = "200", description = "Successfully replaced recipients")
  @ApiResponse(responseCode = "403", description = "The caller may not write the experiment")
  @ApiResponse(responseCode = "404", description = "The experiment does not exist")
  @PutMapping(
      path = ApiPaths.EXPERIMENT_RECIPIENTS,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public RecipientsResponseDto replaceRecipientsOf(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id, @Valid @RequestBody ReplaceRecipientsDto dto) {
    return mapper.toDto(service.replaceRecipientsOf(id, Set.copyOf(dto.userIds())));
  }

  @Operation(
      summary = "Get the users that may become Recipients of an experiment",
      description =
          "The enabled members of the experiment's read and read + write groups, each once,"
              + " sorted by name. Monteis-Admins and global editors are no candidates.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved candidates")
  @ApiResponse(responseCode = "403", description = "The caller may not write the experiment")
  @ApiResponse(responseCode = "404", description = "The experiment does not exist")
  @GetMapping(
      path = ApiPaths.EXPERIMENT_RECIPIENT_CANDIDATES,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public List<DirectoryUserDto> findRecipientCandidatesOf(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id) {
    return mapper.toDtos(service.findRecipientCandidatesOf(id));
  }
}
