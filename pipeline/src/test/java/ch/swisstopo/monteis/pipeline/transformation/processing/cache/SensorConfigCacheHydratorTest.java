package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class SensorConfigCacheHydratorTest {

  @Mock private SensorConfigCache cacheService;

  @Mock private Acknowledgment ack;

  @InjectMocks private SensorConfigCacheHydrator hydrator;

  @Test
  void should_update_cache_and_acknowledge_message() {
    // given
    ActiveSensorConfig config =
        new ActiveSensorConfig(new SensorConfig("deviceA", "x + 1", 100.0, 0.0, 1));

    // when
    hydrator.consumeSensorConfigUpdate(config.getConfig(), ack);

    // then
    then(cacheService).should().updateSensorConfig(config.getConfig());
    then(ack).should().acknowledge();
  }
}
