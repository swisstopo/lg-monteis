package ch.swisstopo.monteis.core.modules.identity.web;

import ch.swisstopo.monteis.core.infrastructure.security.AuthorityChecks;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthorities;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import ch.swisstopo.monteis.core.modules.identity.web.dto.CurrentUserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

  @Operation(
      summary = "Get the current caller's derived permissions",
      description =
          "Reflects the authorities already granted by MonteisJwtAuthenticationConverter.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved current user info")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CurrentUserDto> getCurrentUser(Authentication authentication) {
    boolean canWrite =
        AuthorityChecks.hasAuthority(authentication, MonteisAuthorities.ADMIN_AUTHORITY);
    boolean canWriteAllExperiments = AuthorityChecks.canWriteAllExperiments(authentication);
    List<UUID> writeExperimentIds =
        authentication.getPrincipal() instanceof MonteisPrincipal principal
            ? principal.getWriteExperimentIds()
            : List.of();
    return ResponseEntity.ok(
        new CurrentUserDto(canWrite, canWriteAllExperiments, writeExperimentIds));
  }
}
