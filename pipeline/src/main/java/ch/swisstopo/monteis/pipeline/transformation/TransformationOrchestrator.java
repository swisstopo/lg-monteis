package ch.swisstopo.monteis.pipeline.transformation;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.transformation.standardization.SIStandardizer;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundsValidator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class TransformationOrchestrator {

    private final SIStandardizer siStandardizer;
    private final BoundsValidator boundsValidator;

    public TransformationOrchestrator(SIStandardizer siStandardizer, BoundsValidator boundsValidator) {
        this.siStandardizer = siStandardizer;
        this.boundsValidator = boundsValidator;
    }

    public SensorReadingRecord transform(String sensorId, Double rawValue, String rawTimestamp, SensorConfig config) {
        OffsetDateTime timestamp;
        try {
            timestamp = OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(rawTimestamp)), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            // Translate parsing error
            throw new TransformationException("Invalid epoch timestamp format: '" + rawTimestamp + "'", e);
        }

        return transform(sensorId, rawValue, timestamp, config);
    }

    public SensorReadingRecord transform(String sensorId, Double rawValue, OffsetDateTime timestamp, SensorConfig config) {
        try {
            // 1. Standardize the value
            Double standardizedToSI = siStandardizer.standardizeToSI(rawValue, config);

            // 2. Validate the value
            RangeCategory status = boundsValidator.evaluateBounds(sensorId, standardizedToSI, config);

            // 3. Map to jOOQ record
            SensorReadingRecord sensorReading = new SensorReadingRecord();
            sensorReading.setSensorId(sensorId);
            sensorReading.setTimestamp(timestamp);
            sensorReading.setRawValue(rawValue);
            sensorReading.setNormValue(standardizedToSI);
            sensorReading.setStatus(status);
            sensorReading.setVersion(config.getVersion().shortValue());

            return sensorReading;
        } catch (ArithmeticException | NullPointerException e) {
            throw new TransformationException("Failed to calculate normalized value", e, rawValue);
        }
    }
}