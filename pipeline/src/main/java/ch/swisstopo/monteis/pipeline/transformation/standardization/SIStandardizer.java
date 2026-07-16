package ch.swisstopo.monteis.pipeline.transformation.standardization;

import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SIStandardizer {

  private static final Logger log = LoggerFactory.getLogger(SIStandardizer.class);

  /**
   * Converts the raw reading to the SI standard using the versioned formula.
   *
   * @param rawValue     The raw reading from the sensor.
   * @param activeConfig The active configuration containing the sensor ID, version, and formula.
   * @return The standardized SI value.
   * @throws TransformationException if the parsed formula throws a math or syntax error during evaluation.
   */
  public Double standardizeToSI(Double rawValue, ActiveSensorConfig activeConfig) {
    try {
      return activeConfig.evaluate(rawValue);

    } catch (IllegalArgumentException | ArithmeticException e) {

      log.error(
          "Math calculation failed for sensor {}. Formula: '{}', Raw Value: {}",
          activeConfig.getConfig().getSensorId(),
          activeConfig.getConfig().getFormula(),
          rawValue,
          e);

      throw new TransformationException("Math calculation failed: " + e.getMessage(), e, rawValue);
    }
  }
}
