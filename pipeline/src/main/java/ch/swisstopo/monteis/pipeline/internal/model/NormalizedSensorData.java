package ch.swisstopo.monteis.pipeline.internal.model;

/**
 * {@code dasKey} is the composite DAS ingest key ({@code <DAS>__<das_sensor_alias>__<das_parameter_alias>}),
 * not a raw sensor identifier - see {@code ch.swisstopo.monteis.contracts.DasKey}.
 */
public record NormalizedSensorData(String dasKey, String ts, Double value) {}
