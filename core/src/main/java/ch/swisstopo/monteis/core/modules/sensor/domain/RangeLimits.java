package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.util.Map;

// Measuring range of the sensor, from the manufacturer's config file.
public record RangeLimits(Double lower, Double upper) {
  public RangeLimits {
    if (lower > upper) {
      throw new ObjectBusinessValidationException(
          "sensor.rangeLimits.invalid", Map.of("lower", lower, "upper", upper));
    }
  }
}
