package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.junit.jupiter.api.Test;

class SensorConfigCacheTest {

  private final SensorConfigCache cache = new SensorConfigCache();

  @Test
  void should_return_default_config_when_sensor_does_not_exist() {
    // given
    String unknownSensorId = "device-unknown";

    // when
    ActiveSensorConfig result = cache.getActiveConfig(unknownSensorId);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getConfig().getSensorId()).isEqualTo("UNKNOWN_SENSOR");
    assertThat(result.getConfig().getVersion()).isZero();
  }

  @Test
  void should_store_and_return_config_when_updated() {
    // given
    String sensorId = "deviceA";
    SensorConfig config = new SensorConfig(sensorId, "x + 1", 100.0, 0.0, 1);

    // when
    cache.updateSensorConfig(config);
    ActiveSensorConfig result = cache.getActiveConfig(sensorId);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getConfig()).isEqualTo(config);
  }

  @Test
  void should_overwrite_existing_config_when_updated_again() {
    // given
    String sensorId = "deviceB";
    SensorConfig initialConfig = new SensorConfig(sensorId, "x + 1", 50.0, -10.0, 1);
    SensorConfig newerConfig = new SensorConfig(sensorId, "x + 2", 60.0, -20.0, 2);

    cache.updateSensorConfig(initialConfig);

    // when
    cache.updateSensorConfig(newerConfig);
    ActiveSensorConfig result = cache.getActiveConfig(sensorId);

    // then
    assertThat(result.getConfig().getVersion()).isEqualTo(2);
    assertThat(result.getConfig().getUpperBound()).isEqualTo(60.0);
  }
}
