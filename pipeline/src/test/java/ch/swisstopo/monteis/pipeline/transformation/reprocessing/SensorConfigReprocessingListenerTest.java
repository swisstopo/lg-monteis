package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
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

  @Mock private ReprocessService reprocessService;

  @Mock private Acknowledgment ack;

  @InjectMocks private SensorConfigReprocessingListener listener;

  @Captor private ArgumentCaptor<ActiveSensorConfig> activeConfigCaptor;

  @Test
  void should_trigger_reprocessing_and_acknowledge_on_success() {
    // given
    SensorConfig config = new SensorConfig("deviceA", "x", 100.0, 0.0, 2);

    // when
    listener.consumeSensorConfigUpdate(config, ack);

    // then
    then(reprocessService).should().checkAndReprocessHistoricalData(activeConfigCaptor.capture());
    assertThat(activeConfigCaptor.getValue().getConfig()).isEqualTo(config);
    then(ack).should().acknowledge();
  }

  @Test
  void should_not_acknowledge_if_reprocessing_fails() {
    // given
    SensorConfig config = new SensorConfig("deviceB", "x", 50.0, -10.0, 3);

    // Simulate a failure during the chunk processing (e.g., DB connection dropped)
    willThrow(new RuntimeException("Database timeout during reprocessing"))
        .given(reprocessService)
        .checkAndReprocessHistoricalData(any(ActiveSensorConfig.class));

    // when
    // The exception must bubble up to Spring Kafka to trigger the retry/error handler
    assertThrows(RuntimeException.class, () -> listener.consumeSensorConfigUpdate(config, ack));

    // then
    then(reprocessService).should().checkAndReprocessHistoricalData(any(ActiveSensorConfig.class));
    then(ack).shouldHaveNoInteractions();
  }

  @Test
  void should_acknowledge_without_reprocessing_if_formula_is_invalid() {
    // given
    SensorConfig invalidConfig =
        new SensorConfig("deviceC", "&&--invalid-syntax--$$", 50.0, -10.0, 3);

    // when
    listener.consumeSensorConfigUpdate(invalidConfig, ack);

    // then
    then(reprocessService).shouldHaveNoInteractions();
    then(ack).should().acknowledge();
  }
}
