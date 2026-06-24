package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SensorConfigCacheHydratorTest {

    @Mock
    private SensorConfigCache cacheService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private SensorConfigCacheHydrator hydrator;

    @Test
    void should_update_cache_and_acknowledge_message() {
        // given
        SensorConfig config = new SensorConfig(
                "deviceA", Map.of(), 100.0, 0.0, 1
        );

        // when
        hydrator.consumeSensorConfigUpdate(config, ack);

        // then
        then(cacheService).should().updateSensorConfig(config);
        then(ack).should().acknowledge();
    }
}