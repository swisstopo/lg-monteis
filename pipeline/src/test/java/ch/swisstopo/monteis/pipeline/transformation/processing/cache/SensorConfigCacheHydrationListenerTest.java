package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.SensorConfigMessageHandler;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class SensorConfigCacheHydrationListenerTest {

  @Mock private SensorConfigCache cacheService;

  @Mock private SensorConfigMessageHandler messageProcessor;

  @Mock private Acknowledgment ack;

  @InjectMocks private SensorConfigCacheHydrationListener hydrator;

  @Test
  void should_delegate_to_processor_and_update_cache() {
    // given
    String dasKey = DasKey.compose(Das.SOL_EXPERTS, "deviceA", "temperature");
    SensorParameterConfig config =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceA", "temperature", UUID.randomUUID(), "x + 1", 100.0, 0.0, 1);

    // Simulate the messageProcessor successfully executing the lambda function passed to it
    willAnswer(
            invocation -> {
              SensorParameterConfig passedConfig = invocation.getArgument(0);
              Consumer<SensorParameterConfig> businessLogicLambda = invocation.getArgument(3);

              businessLogicLambda.accept(passedConfig); // Execute the lambda
              return null;
            })
        .given(messageProcessor)
        .processSafely(any(), any(), any(), any());

    // when
    hydrator.consumeSensorConfigUpdate(config, dasKey, ack);

    // then
    then(messageProcessor).should().processSafely(eq(config), eq(dasKey), eq(ack), any());

    then(cacheService).should().updateSensorConfig(config);
  }
}
