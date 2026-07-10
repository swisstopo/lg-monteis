package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.BusinessValidationException;
import java.util.Map;

public record Bounds(Double lower, Double upper) {
  public Bounds {
    if (lower > upper) {
      throw new BusinessValidationException(
          "lowerBound", lower, "sensor.bounds.invalid", Map.of("lower", lower, "upper", upper));
    }
  }
}
