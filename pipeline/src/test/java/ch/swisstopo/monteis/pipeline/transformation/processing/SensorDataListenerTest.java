package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SensorDataListenerTest {

    @Mock
    private ProcessService processService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private SensorDataListener listener;

    @Test
    void should_delegate_batch_to_process_service() {
        // given
        List<NormalizedSensorData> batch = List.of(
                new NormalizedSensorData("deviceA", "2026-06-23T10:00:00Z", 10.5),
                new NormalizedSensorData("deviceB", "2026-06-23T10:05:00Z", 20.0)
        );

        // when
        listener.consumeNormalizedSensorData(batch, ack);

        // then
        then(processService).should().processAndPersist(batch, ack);
    }
}