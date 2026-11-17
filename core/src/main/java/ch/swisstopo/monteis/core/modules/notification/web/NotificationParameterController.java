package ch.swisstopo.monteis.core.modules.notification.web;

import ch.swisstopo.monteis.core.infrastructure.api.ApiPaths;
import ch.swisstopo.monteis.core.modules.notification.service.NotificationParameterService;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotificationParameterResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationParameterController {

  private final NotificationParameterService service;

  public NotificationParameterController(NotificationParameterService service) {
    this.service = service;
  }

  @Operation(
      summary = "Get the notification parameters of an experiment",
      description =
          "The sensor parameters of the experiment that are Notification active, with their"
              + " newest measurement, sorted by sensor name, then parameter name. Not paged."
              + " Until the flag is readable (MON-235), every sensor parameter of the experiment.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved notification parameters")
  @ApiResponse(responseCode = "403", description = "The caller may not write the experiment")
  @ApiResponse(responseCode = "404", description = "The experiment does not exist")
  @GetMapping(
      path = ApiPaths.EXPERIMENT_NOTIFICATION_PARAMETERS,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public List<NotificationParameterResponseDto> findNotificationParametersOf(
      @PathVariable(ApiPaths.EXPERIMENT_ID) UUID id) {
    return service.findNotificationParametersOf(id);
  }
}
