package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.SkippedSensor;
import java.util.List;
import java.util.UUID;

public record FulcrumRefreshResponseDto(int checked, int updated, List<SkippedSensorDto> skipped) {

  public record SkippedSensorDto(UUID sensorId, SkippedSensor.Reason reason) {}
}
