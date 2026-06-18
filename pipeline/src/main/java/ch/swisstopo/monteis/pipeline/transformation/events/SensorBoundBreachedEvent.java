package ch.swisstopo.monteis.pipeline.transformation.events;

import java.time.Instant;

public record SensorBoundBreachedEvent(
        String sensorId,
        Double standardizedValue,
        Double limitViolated,
        BoundType boundType,
        Instant timestamp
) {
    /**
     * Defines whether the value exceeded the upper bound or dropped below the lower bound.
     */
    public enum BoundType {
        UPPER, LOWER
    }
}