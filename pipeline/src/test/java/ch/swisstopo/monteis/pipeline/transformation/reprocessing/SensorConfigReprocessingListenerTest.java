package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class SensorConfigReprocessingListenerTest {

    @Mock
    private ReprocessService reprocessService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private SensorConfigReprocessingListener listener;

    @Test
    void shouldTriggerReprocessingAndAcknowledgeOnSuccess() {
        // given
        SensorConfig config = new SensorConfig("deviceA", Map.of(), 100.0, 0.0, 2);

        // when
        listener.consumeSensorConfigUpdate(config, ack);

        // then
        then(reprocessService).should().checkAndReprocessHistoricalData(config);
        then(ack).should().acknowledge();
    }

    @Test
    void shouldNotAcknowledgeIfReprocessingFails() {
        // given
        SensorConfig config = new SensorConfig("deviceB", Map.of(), 50.0, -10.0, 3);

        // Simulate a failure during the chunk processing (e.g., DB connection dropped)
        willThrow(new RuntimeException("Database timeout during reprocessing"))
                .given(reprocessService).checkAndReprocessHistoricalData(config);

        // when
        // The exception must bubble up to Spring Kafka to trigger the retry/error handler
        assertThrows(RuntimeException.class, () -> listener.consumeSensorConfigUpdate(config, ack));

        // then
        then(reprocessService).should().checkAndReprocessHistoricalData(config);
        then(ack).shouldHaveNoInteractions();
    }
}