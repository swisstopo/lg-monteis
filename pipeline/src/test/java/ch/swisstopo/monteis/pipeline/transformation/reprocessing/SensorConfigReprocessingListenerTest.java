package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.SensorConfigMessageHandler;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class SensorConfigReprocessingListenerTest {

  @Mock private SensorReprocessingOrchestrator sensorReprocessingOrchestrator;

  @Mock private SensorConfigMessageHandler messageProcessor;

  @Mock private Acknowledgment ack;

  @InjectMocks private SensorConfigReprocessingListener listener;

  @Captor private ArgumentCaptor<ActiveSensorConfig> activeConfigCaptor;

  @Test
  void should_delegate_to_processor_and_trigger_reprocessing() {
    // given
    String sensorId = "deviceA";
    SensorConfig config = new SensorConfig(sensorId, "x * 2.5", 100.0, 0.0, 2);

    // Simulate the messageProcessor instantly executing the lambda function passed to it
    willAnswer(
            invocation -> {
              SensorConfig passedConfig = invocation.getArgument(0);
              Consumer<SensorConfig> businessLogicLambda = invocation.getArgument(3);

              businessLogicLambda.accept(passedConfig); // Execute the lambda
              return null;
            })
        .given(messageProcessor)
        .processSafely(any(), any(), any(), any());

    // when
    listener.consumeSensorConfigUpdate(config, sensorId, ack);

    // then
    then(messageProcessor).should().processSafely(eq(config), eq(sensorId), eq(ack), any());

    then(sensorReprocessingOrchestrator)
        .should()
        .checkAndReprocessHistoricalData(activeConfigCaptor.capture());
    assertThat(activeConfigCaptor.getValue().getConfig()).isEqualTo(config);
  }
}
