package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FulcrumRefreshResponseDto(int checked, int updated, List<SkippedSensorDto> skipped) {

  public record SkippedSensorDto(
      UUID sensorId,
      @Schema(description = "Null for NOT_FOUND: the sensor is unknown or hidden from the caller")
          @Nullable String sensorName,
      SkippedSensor.Reason reason) {}
}
