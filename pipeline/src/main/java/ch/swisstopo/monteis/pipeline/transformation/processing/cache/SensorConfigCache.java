package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.SensorConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigCache {

  private final ConcurrentMap<String, ActiveSensorConfig> cache = new ConcurrentHashMap<>();

  private static final String DEFAULT_SENSOR_ID = "UNKNOWN_SENSOR";
  private static final String DEFAULT_FORMULA = "x";
  private static final Integer DEFAULT_SENSOR_VERSION = 0;
  private static final Double DEFAULT_SENSOR_UPPER_BOUND = Double.MAX_VALUE;
  private static final Double DEFAULT_SENSOR_LOWER_BOUND = -Double.MAX_VALUE;

  private static final SensorConfig DEFAULT_SENSOR_CONFIG =
      new SensorConfig(
          DEFAULT_SENSOR_ID,
          DEFAULT_FORMULA,
          DEFAULT_SENSOR_UPPER_BOUND,
          DEFAULT_SENSOR_LOWER_BOUND,
          DEFAULT_SENSOR_VERSION);

  private static final ActiveSensorConfig DEFAULT_ACTIVE_CONFIG =
      new ActiveSensorConfig(DEFAULT_SENSOR_CONFIG);

  public ActiveSensorConfig getActiveConfig(String sensorId) {
    return cache.getOrDefault(sensorId, DEFAULT_ACTIVE_CONFIG);
  }

  public void updateSensorConfig(SensorConfig sensorConfig) {
    ActiveSensorConfig activeConfig = new ActiveSensorConfig(sensorConfig);
    cache.put(sensorConfig.getSensorId(), activeConfig);
  }
}
