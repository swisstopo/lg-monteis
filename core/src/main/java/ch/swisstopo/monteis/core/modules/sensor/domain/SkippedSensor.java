package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SkippedSensor(UUID sensorId, @Nullable String sensorName, Reason reason) {

  // no name: unknown or hidden by RLS, a name would tell what the caller may not see
  public static SkippedSensor notFound(UUID sensorId) {
    return new SkippedSensor(sensorId, null, Reason.NOT_FOUND);
  }

  public static SkippedSensor of(Sensor sensor, Reason reason) {
    return new SkippedSensor(sensor.getId(), sensor.getName(), reason);
  }

  public enum Reason {
    NOT_FOUND,
    NOT_IN_FULCRUM,
    COORDINATES_MISSING,
    CHANGED_MEANWHILE
  }
}
