package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.util.Map;

public record AlarmBounds(Double lower, Double upper) {
  public AlarmBounds {
    if (lower > upper) {
      throw new ObjectBusinessValidationException(
          "sensor.alarmBounds.invalid", Map.of("lower", lower, "upper", upper));
    }
  }
}
