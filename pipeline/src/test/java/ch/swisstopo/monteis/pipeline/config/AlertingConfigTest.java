package ch.swisstopo.monteis.pipeline.config;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;

import ch.swisstopo.monteis.pipeline.egress.AlertPublisher;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent.BoundType;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

class AlertingConfigTest {

  private static final String ALERT_TOPIC = "sensor-alerts";
  private static final Duration NON_BLOCKING = Duration.ofSeconds(2);

  private KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;
  private ApplicationContextRunner contextRunner;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(AlertingConfig.class)
            .withBean(
                "alertPublisher",
                AlertPublisher.class,
                () -> new AlertPublisher(kafkaTemplate, ALERT_TOPIC));
  }

  @Test
  void should_publish_on_the_alert_executor_without_blocking_the_caller() {
    // given
    CountDownLatch releaseSend = new CountDownLatch(1);
    given(kafkaTemplate.send(anyString(), anyString(), any(SensorBoundBreachedEvent.class)))
        .willAnswer(
            invocation -> {
              releaseSend.await(5, TimeUnit.SECONDS);
              return CompletableFuture.completedFuture(null);
            });

    contextRunner.run(
        context -> {
          try {
            // when / then
            assertTimeoutPreemptively(NON_BLOCKING, () -> context.publishEvent(event("s1")));
            then(kafkaTemplate).should(timeout(1000)).send(eq(ALERT_TOPIC), eq("s1"), any());
          } finally {
            releaseSend.countDown();
          }
        });
  }

  @Test
  void should_drop_alert_without_blocking_or_failing_the_caller_when_queue_is_full() {
    // given
    CountDownLatch sendStarted = new CountDownLatch(1);
    CountDownLatch releaseSend = new CountDownLatch(1);
    given(kafkaTemplate.send(anyString(), anyString(), any(SensorBoundBreachedEvent.class)))
        .willAnswer(
            invocation -> {
              sendStarted.countDown();
              releaseSend.await(5, TimeUnit.SECONDS);
              return CompletableFuture.completedFuture(null);
            });

    contextRunner
        .withPropertyValues("app.pipeline.alerts.queue-capacity=1")
        .run(
            context -> {
              try {
                // when
                assertTimeoutPreemptively(
                    NON_BLOCKING,
                    () -> {
                      context.publishEvent(event("s1"));
                      sendStarted.await();
                      context.publishEvent(event("s2"));
                      context.publishEvent(event("s3"));
                    });
              } finally {
                releaseSend.countDown();
              }

              // then
              then(kafkaTemplate).should(timeout(1000)).send(eq(ALERT_TOPIC), eq("s2"), any());
              then(kafkaTemplate).should(after(200).never()).send(eq(ALERT_TOPIC), eq("s3"), any());
            });
  }

  @Test
  void should_drain_queued_alerts_before_closing_the_producer_on_shutdown() {
    // given
    CountDownLatch sendStarted = new CountDownLatch(1);
    given(kafkaTemplate.send(anyString(), anyString(), any(SensorBoundBreachedEvent.class)))
        .willAnswer(
            invocation -> {
              sendStarted.countDown();
              Thread.sleep(200);
              return CompletableFuture.completedFuture(null);
            });

    contextRunner.run(
        context -> {
          context.publishEvent(event("s1"));
          sendStarted.await(1, TimeUnit.SECONDS);
          context.publishEvent(event("s2"));
          context.publishEvent(event("s3"));

          // when
          context.close();

          // then
          InOrder inOrder = inOrder(kafkaTemplate);
          inOrder
              .verify(kafkaTemplate, times(3))
              .send(eq(ALERT_TOPIC), anyString(), any(SensorBoundBreachedEvent.class));
          inOrder.verify(kafkaTemplate).destroy();
        });
  }

  private static SensorBoundBreachedEvent event(String sensorId) {
    return new SensorBoundBreachedEvent(sensorId, 200.0, 100.0, BoundType.UPPER, Instant.now());
  }
}
