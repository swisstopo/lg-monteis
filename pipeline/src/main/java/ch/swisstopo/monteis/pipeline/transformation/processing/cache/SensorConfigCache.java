package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigCache {

  // Keyed by the composite DAS ingest key (<DAS>__<das_sensor_alias>__<das_parameter_alias>), not a
  // raw alias - see contracts.DasKey.
  private final ConcurrentMap<String, ActiveSensorConfig> cache = new ConcurrentHashMap<>();

  private static final String DEFAULT_DAS_SENSOR_ALIAS = "UNKNOWN_SENSOR";
  private static final String DEFAULT_FORMULA = "x";
  private static final Integer DEFAULT_SENSOR_VERSION = 0;
  private static final Double DEFAULT_SENSOR_UPPER_BOUND = Double.MAX_VALUE;
  private static final Double DEFAULT_SENSOR_LOWER_BOUND = -Double.MAX_VALUE;

  private static final SensorParameterConfig DEFAULT_SENSOR_CONFIG =
      new SensorParameterConfig(
          null,
          DEFAULT_DAS_SENSOR_ALIAS,
          null,
          null,
          DEFAULT_FORMULA,
          DEFAULT_SENSOR_UPPER_BOUND,
          DEFAULT_SENSOR_LOWER_BOUND,
          DEFAULT_SENSOR_VERSION);

  private static final ActiveSensorConfig DEFAULT_ACTIVE_CONFIG =
      new ActiveSensorConfig(DEFAULT_SENSOR_CONFIG);

  public ActiveSensorConfig getActiveConfig(String dasKey) {
    return cache.getOrDefault(dasKey, DEFAULT_ACTIVE_CONFIG);
  }

  public void updateSensorConfig(SensorParameterConfig sensorParameterConfig) {
    ActiveSensorConfig activeConfig = new ActiveSensorConfig(sensorParameterConfig);
    String dasKey =
        DasKey.compose(
            sensorParameterConfig.getDas(),
            sensorParameterConfig.getDasSensorAlias(),
            sensorParameterConfig.getDasParameterAlias());
    cache.put(dasKey, activeConfig);
  }
}
