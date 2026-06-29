package ch.swisstopo.monteis.pipeline.transformation;

import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.standardization.SIStandardizer;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundStatus;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundsValidator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class TransformationOrchestrator {

  private final SIStandardizer siStandardizer;
  private final BoundsValidator boundsValidator;

  public TransformationOrchestrator(
      SIStandardizer siStandardizer, BoundsValidator boundsValidator) {
    this.siStandardizer = siStandardizer;
    this.boundsValidator = boundsValidator;
  }

  public SensorReadingRecord transform(
      String sensorId, Double rawValue, String rawTimestamp, ActiveSensorConfig activeConfig) {
    OffsetDateTime timestamp;
    try {
      timestamp =
          OffsetDateTime.ofInstant(
              Instant.ofEpochMilli(Long.parseLong(rawTimestamp)), ZoneOffset.UTC);
    } catch (NumberFormatException e) {
      throw new TransformationException(
          "Invalid epoch timestamp format: '" + rawTimestamp + "'", e, rawValue);
    }

    return transform(sensorId, rawValue, timestamp, activeConfig);
  }

  public SensorReadingRecord transform(
      String sensorId, Double rawValue, OffsetDateTime timestamp, ActiveSensorConfig activeConfig) {
    // 1. Standardize the value
    Double standardizedToSI = siStandardizer.standardizeToSI(rawValue, activeConfig);

    // 2. Validate the value
    BoundStatus status =
        boundsValidator.evaluateBounds(sensorId, standardizedToSI, activeConfig.getConfig());

    // 3. Map to jOOQ record
    SensorReadingRecord sensorReading = new SensorReadingRecord();
    sensorReading.setSensorId(sensorId);
    sensorReading.setTimestamp(timestamp);
    sensorReading.setRawValue(rawValue);
    sensorReading.setNormValue(standardizedToSI);
    sensorReading.setStatus(toRangeCategory(status));
    sensorReading.setVersion(activeConfig.getConfig().getVersion().shortValue());

    return sensorReading;
  }

  private RangeCategory toRangeCategory(BoundStatus status) {
    return switch (status) {
      case OK -> RangeCategory.correct;
      case TOO_LOW -> RangeCategory.too_low;
      case TOO_HIGH -> RangeCategory.too_high;
    };
  }
}
