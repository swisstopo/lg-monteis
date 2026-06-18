package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SensorConfigCache {

    private final ConcurrentMap<String, SensorConfig> cache = new ConcurrentHashMap<>();

    private static final String DEFAULT_SENSOR_ID = "UNKNOWN_SENSOR";
    private static final Integer DEFAULT_SENSOR_VERSION = 0;
    private static final Double DEFAULT_SENSOR_UPPER_BOUND = Double.MAX_VALUE;
    private static final Double DEFAULT_SENSOR_LOWER_BOUND = Double.MIN_VALUE;

    private static final SensorConfig DEFAULT_SENSOR_CONFIG = new SensorConfig(
            DEFAULT_SENSOR_ID,
            Map.of(),
            DEFAULT_SENSOR_UPPER_BOUND,
            DEFAULT_SENSOR_LOWER_BOUND,
            DEFAULT_SENSOR_VERSION
    );

    public SensorConfig getSensorConfig(String sensorId) {
        return cache.getOrDefault(sensorId, DEFAULT_SENSOR_CONFIG);
    }

    protected void updateSensorConfig(SensorConfig sensorConfig) {
        cache.put(sensorConfig.getSensorId(), sensorConfig);
    }
}