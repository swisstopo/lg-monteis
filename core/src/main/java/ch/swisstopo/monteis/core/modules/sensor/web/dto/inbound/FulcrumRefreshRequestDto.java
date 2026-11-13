package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FulcrumRefreshRequestDto(
    // empty list rejected, not read as "all": a webapp bug must not refresh everything
    @Schema(
            description =
                "The sensors to refresh, 1 to 1000. Without it every sensor the caller can see is"
                    + " refreshed.")
        @Size(min = 1, max = MAX_SENSOR_IDS)
        @Nullable List<@NotNull UUID> sensorIds) {

  // bounds the work of one synchronous request
  public static final int MAX_SENSOR_IDS = 1000;

  public Optional<List<UUID>> selectedSensorIds() {
    return Optional.ofNullable(sensorIds);
  }
}
