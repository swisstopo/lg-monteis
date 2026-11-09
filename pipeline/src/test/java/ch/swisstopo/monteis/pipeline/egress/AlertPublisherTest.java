package ch.swisstopo.monteis.pipeline.egress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent.BoundType;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class AlertPublisherTest {

  private static final String ALERT_TOPIC = "sensor-alerts";

  @Mock private KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;

  private AlertPublisher alertPublisher;

  @BeforeEach
  void setUp() {
    alertPublisher = new AlertPublisher(kafkaTemplate, ALERT_TOPIC);
  }

  @Test
  void should_publish_alert_successfully() {
    // given
    SensorBoundBreachedEvent event = event("deviceA");
    @SuppressWarnings("unchecked")
    SendResult<String, SensorBoundBreachedEvent> sendResult = mock(SendResult.class);
    given(kafkaTemplate.send(ALERT_TOPIC, "deviceA", event))
        .willReturn(CompletableFuture.completedFuture(sendResult));

    // when
    alertPublisher.handleBoundBreach(event);

    // then
    then(kafkaTemplate).should().send(ALERT_TOPIC, "deviceA", event);
  }

  @Test
  void should_handle_publishing_failure_gracefully() {
    // given
    SensorBoundBreachedEvent event = event("deviceB");
    CompletableFuture<SendResult<String, SensorBoundBreachedEvent>> failedFuture =
        new CompletableFuture<>();
    failedFuture.completeExceptionally(new KafkaException("Kafka Broker timeout"));
    given(kafkaTemplate.send(ALERT_TOPIC, "deviceB", event)).willReturn(failedFuture);

    // when / then
    assertThatCode(() -> alertPublisher.handleBoundBreach(event)).doesNotThrowAnyException();
    then(kafkaTemplate).should().send(ALERT_TOPIC, "deviceB", event);
  }

  @Test
  void should_not_propagate_synchronous_send_failure_to_caller() {
    // given
    SensorBoundBreachedEvent event = event("deviceC");
    given(kafkaTemplate.send(ALERT_TOPIC, "deviceC", event))
        .willThrow(new KafkaException("Topic sensor-alerts not present in metadata"));

    // when / then
    assertThatCode(() -> alertPublisher.handleBoundBreach(event)).doesNotThrowAnyException();
    then(kafkaTemplate).should().send(ALERT_TOPIC, "deviceC", event);
  }

  @Test
  void should_not_propagate_any_runtime_exception_from_send() {
    // given
    SensorBoundBreachedEvent event = event("deviceD");
    given(kafkaTemplate.send(ALERT_TOPIC, "deviceD", event))
        .willThrow(
            new IllegalStateException("Cannot perform operation after producer has been closed"));

    // when / then
    assertThatCode(() -> alertPublisher.handleBoundBreach(event)).doesNotThrowAnyException();
  }

  @Test
  void should_close_the_template_on_destroy() {
    // when
    alertPublisher.destroy();

    // then
    then(kafkaTemplate).should().destroy();
  }

  private static SensorBoundBreachedEvent event(String sensorId) {
    return new SensorBoundBreachedEvent(sensorId, 200.0, 100.0, BoundType.UPPER, Instant.now());
  }
}
