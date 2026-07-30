package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.util.Map;

public record AlarmLimits(Double lower, Double upper) {
  public AlarmLimits {
    if (lower > upper) {
      throw new ObjectBusinessValidationException(
          "sensor.alarmLimits.invalid", Map.of("lower", lower, "upper", upper));
    }
  }
}
