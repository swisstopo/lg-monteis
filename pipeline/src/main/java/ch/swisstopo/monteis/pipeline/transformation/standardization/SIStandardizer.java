package ch.swisstopo.monteis.pipeline.transformation.standardization;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.springframework.stereotype.Service;

@Service
public class SIStandardizer {

  // Converts the raw reading to the SI standard using the versioned formula
  public Double standardizeToSI(Double rawValue, SensorConfig config) {
    Double siStandardValue = rawValue;
    // TODO: Implement the calculation based on the value and formula
    return siStandardValue;
  }
}
