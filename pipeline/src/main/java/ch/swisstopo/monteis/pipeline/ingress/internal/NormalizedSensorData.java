package ch.swisstopo.monteis.pipeline.ingress.internal;

public record NormalizedSensorData(
        String sensorId,
        String ts,
        Double value
) {
}