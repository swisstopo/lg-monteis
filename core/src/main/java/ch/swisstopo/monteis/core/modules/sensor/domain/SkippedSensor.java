package ch.swisstopo.monteis.core.modules.sensor.domain;

import java.util.UUID;

public record SkippedSensor(UUID sensorId, Reason reason) {

  public enum Reason {
    NOT_FOUND,
    NO_FULCRUM_ID,
    NOT_IN_FULCRUM,
    COORDINATES_MISSING,
    CHANGED_MEANWHILE
  }
}
