package ch.swisstopo.monteis.pipeline.egress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;

import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent.BoundType;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class AlertPublisherTest {

  @Mock private KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;

  private AlertPublisher alertPublisher;

  private final String alertTopic = "sensor-alerts";

  @AfterEach
  void tearDown() {
    alertPublisher.destroy();
  }

  @Test
  void should_publish_alert_successfully() {
    // given
    alertPublisher = new AlertPublisher(kafkaTemplate, alertTopic, 10);
    SensorBoundBreachedEvent event =
        new SensorBoundBreachedEvent("deviceA", 150.5, 100.0, BoundType.UPPER, Instant.now());

    // Simulate a successful Kafka send
    @SuppressWarnings("unchecked")
    SendResult<String, SensorBoundBreachedEvent> sendResult = mock(SendResult.class);
    given(kafkaTemplate.send(alertTopic, event.sensorId(), event))
        .willReturn(CompletableFuture.completedFuture(sendResult));

    // when
    alertPublisher.handleBoundBreach(event);

    // then
    then(kafkaTemplate).should(timeout(1000)).send(alertTopic, "deviceA", event);
  }

  @Test
  void should_not_propagate_synchronous_send_failure_to_caller() {
    // given
    alertPublisher = new AlertPublisher(kafkaTemplate, alertTopic, 10);
    SensorBoundBreachedEvent event =
        new SensorBoundBreachedEvent("deviceB", 200.0, 100.0, BoundType.UPPER, Instant.now());
    given(kafkaTemplate.send(alertTopic, event.sensorId(), event))
        .willThrow(new KafkaException("Topic sensor-alerts not present in metadata"));

    // when / then
    assertThatCode(() -> alertPublisher.handleBoundBreach(event)).doesNotThrowAnyException();
    then(kafkaTemplate).should(timeout(1000)).send(alertTopic, "deviceB", event);
  }

  @Test
  void should_drop_alert_without_blocking_caller_when_queue_is_full() {
    // given: one alert in flight, room for one queued alert
    alertPublisher = new AlertPublisher(kafkaTemplate, alertTopic, 1);
    CountDownLatch sendStarted = new CountDownLatch(1);
    CountDownLatch releaseSend = new CountDownLatch(1);

    // Simulate send() blocking on topic metadata (max.block.ms)
    given(kafkaTemplate.send(anyString(), anyString(), any(SensorBoundBreachedEvent.class)))
        .willAnswer(
            invocation -> {
              sendStarted.countDown();
              releaseSend.await();
              throw new KafkaException("Send failed");
            });

    // when
    alertPublisher.handleBoundBreach(event("s1"));
    assertThatCode(sendStarted::await).doesNotThrowAnyException();
    alertPublisher.handleBoundBreach(event("s2"));
    alertPublisher.handleBoundBreach(event("s3"));
    releaseSend.countDown();

    // then
    then(kafkaTemplate).should(timeout(1000)).send(eq(alertTopic), eq("s2"), any());
    then(kafkaTemplate).should(after(200).never()).send(eq(alertTopic), eq("s3"), any());
  }

  private static SensorBoundBreachedEvent event(String sensorId) {
    return new SensorBoundBreachedEvent(sensorId, 200.0, 100.0, BoundType.UPPER, Instant.now());
  }
}
