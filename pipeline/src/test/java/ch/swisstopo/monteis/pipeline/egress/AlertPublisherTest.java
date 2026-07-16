package ch.swisstopo.monteis.pipeline.egress;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class AlertPublisherTest {

  @Mock private KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;

  private AlertPublisher alertPublisher;

  private final String alertTopic = "sensor-alerts";

  @BeforeEach
  void setUp() {
    // Instantiate the publisher directly, injecting the mock and our test topic
    alertPublisher = new AlertPublisher(kafkaTemplate, alertTopic);
  }

  @Test
  void should_publish_alert_successfully() {
    // given
    SensorBoundBreachedEvent event =
        new SensorBoundBreachedEvent("deviceA", 150.5, 100.0, BoundType.UPPER, Instant.now());

    // Simulate a successful Kafka send
    @SuppressWarnings("unchecked")
    SendResult<String, SensorBoundBreachedEvent> sendResult = mock(SendResult.class);
    CompletableFuture<SendResult<String, SensorBoundBreachedEvent>> successFuture =
        CompletableFuture.completedFuture(sendResult);

    given(kafkaTemplate.send(alertTopic, event.sensorId(), event)).willReturn(successFuture);

    // when
    alertPublisher.handleBoundBreach(event);

    // then
    then(kafkaTemplate).should().send(alertTopic, "deviceA", event);
  }

  @Test
  void should_handle_publishing_failure_gracefully() {
    // given
    SensorBoundBreachedEvent event =
        new SensorBoundBreachedEvent("deviceB", -10.0, 0.0, BoundType.LOWER, Instant.now());

    // Simulate a failed Kafka send (e.g., broker down, timeout)
    CompletableFuture<SendResult<String, SensorBoundBreachedEvent>> failedFuture =
        new CompletableFuture<>();
    failedFuture.completeExceptionally(new RuntimeException("Kafka Broker timeout"));

    given(kafkaTemplate.send(alertTopic, event.sensorId(), event)).willReturn(failedFuture);

    // when
    alertPublisher.handleBoundBreach(event);

    // then
    then(kafkaTemplate).should().send(alertTopic, "deviceB", event);
  }
}
