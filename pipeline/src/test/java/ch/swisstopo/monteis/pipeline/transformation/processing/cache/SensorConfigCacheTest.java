package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensorConfigCacheTest {

    private final SensorConfigCache cache = new SensorConfigCache();

    @Test
    void shouldReturnDefaultConfigWhenSensorDoesNotExist() {
        // given
        String unknownSensorId = "device-unknown";

        // when
        SensorConfig result = cache.getSensorConfig(unknownSensorId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSensorId()).isEqualTo("UNKNOWN_SENSOR");
        assertThat(result.getVersion()).isZero();
    }

    @Test
    void shouldStoreAndReturnConfigWhenUpdated() {
        // given
        String sensorId = "deviceA";
        SensorConfig config = new SensorConfig(
                sensorId, Map.of(), 100.0, 0.0, 1
        );

        // when
        cache.updateSensorConfig(config);
        SensorConfig result = cache.getSensorConfig(sensorId);

        // then
        assertThat(result).isNotNull().isEqualTo(config);
    }

    @Test
    void shouldOverwriteExistingConfigWhenUpdatedAgain() {
        // given
        String sensorId = "deviceB";
        SensorConfig initialConfig = new SensorConfig(sensorId, Map.of(), 50.0, -10.0, 1);
        SensorConfig newerConfig = new SensorConfig(sensorId, Map.of(), 60.0, -20.0, 2);

        cache.updateSensorConfig(initialConfig);

        // when
        cache.updateSensorConfig(newerConfig);
        SensorConfig result = cache.getSensorConfig(sensorId);

        // then
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getUpperBound()).isEqualTo(60.0);
    }
}