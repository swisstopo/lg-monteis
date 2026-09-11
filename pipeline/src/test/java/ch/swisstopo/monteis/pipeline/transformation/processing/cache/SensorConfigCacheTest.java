package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensorConfigCacheTest {

  private final SensorConfigCache cache = new SensorConfigCache();

  @Test
  void should_return_default_config_when_das_key_does_not_exist() {
    // given
    String unknownDasKey = DasKey.compose(Das.SOL_EXPERTS, "device-unknown", "temperature");

    // when
    ActiveSensorConfig result = cache.getActiveConfig(unknownDasKey);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getConfig().getDasSensorAlias()).isEqualTo("UNKNOWN_SENSOR");
    assertThat(result.getConfig().getVersion()).isZero();
  }

  @Test
  void should_store_and_return_config_when_updated() {
    // given
    SensorParameterConfig config =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceA", "temperature", UUID.randomUUID(), "x + 1", 100.0, 0.0, 1);
    String dasKey = DasKey.compose(Das.SOL_EXPERTS, "deviceA", "temperature");

    // when
    cache.updateSensorConfig(config);
    ActiveSensorConfig result = cache.getActiveConfig(dasKey);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getConfig()).isEqualTo(config);
  }

  @Test
  void should_overwrite_existing_config_when_updated_again() {
    // given
    String dasKey = DasKey.compose(Das.SOL_EXPERTS, "deviceB", "temperature");
    SensorParameterConfig initialConfig =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceB", "temperature", UUID.randomUUID(), "x + 1", 50.0, -10.0, 1);
    SensorParameterConfig newerConfig =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceB", "temperature", UUID.randomUUID(), "x + 2", 60.0, -20.0, 2);

    cache.updateSensorConfig(initialConfig);

    // when
    cache.updateSensorConfig(newerConfig);
    ActiveSensorConfig result = cache.getActiveConfig(dasKey);

    // then
    assertThat(result.getConfig().getVersion()).isEqualTo(2);
    assertThat(result.getConfig().getUpperBound()).isEqualTo(60.0);
  }
}
