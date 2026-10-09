package ch.swisstopo.monteis.core.modules.identity.web;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import ch.swisstopo.monteis.core.modules.identity.web.dto.CurrentUserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

  @Operation(
      operationId = "getCurrentUser",
      summary = "Get the current caller's permissions",
      description =
          "Projects the caller's permissions, for UI gating only; the backend"
              + " enforces every rule itself.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved current user info")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CurrentUserDto> getCurrentUser(MonteisAuthenticationToken authentication) {
    return ResponseEntity.ok(
        new CurrentUserDto(
            authentication.canWriteAll(),
            // empty when canWriteAll (BR4.10); sorted for a stable response
            authentication.writableExperimentIds().stream().sorted().toList()));
  }
}
