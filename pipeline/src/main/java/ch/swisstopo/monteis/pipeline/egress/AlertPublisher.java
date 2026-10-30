package ch.swisstopo.monteis.pipeline.egress;

import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertPublisher implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

  private final KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;
  private final String alertTopic;
  private final ExecutorService alertExecutor;

  public AlertPublisher(
      KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate,
      @Value("${pipeline.kafka.topics.alerts:sensor-alerts}") String alertTopic,
      @Value("${pipeline.alerts.queue-capacity:1000}") int queueCapacity) {
    this.kafkaTemplate = kafkaTemplate;
    this.alertTopic = alertTopic;
    this.alertExecutor =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            Thread.ofPlatform().name("alert-publisher").daemon().factory());
  }

  /** Listens for the internal Spring Event and queues it for publishing to the alert topic. */
  @EventListener
  public void handleBoundBreach(SensorBoundBreachedEvent event) {
    try {
      alertExecutor.execute(() -> send(event));
    } catch (RejectedExecutionException ex) {
      log.warn(
          "Alert queue full, dropping alert for sensor {}: value {} exceeded {} bound of {} at {}",
          event.sensorId(),
          event.standardizedValue(),
          event.boundType(),
          event.limitViolated(),
          event.timestamp());
    }
  }

  private void send(SensorBoundBreachedEvent event) {
    log.info(
        "Preparing to send alert for sensor {}: value {} exceeded {} bound of {}",
        event.sensorId(),
        event.standardizedValue(),
        event.boundType(),
        event.limitViolated());

    // We use the sensorId as the Kafka Message Key.
    // The event object is automatically serialized to JSON by Spring Kafka's message converter.
    try {
      kafkaTemplate
          .send(alertTopic, event.sensorId(), event)
          .whenComplete(
              (result, ex) -> {
                if (ex == null) {
                  log.debug("Successfully published alert for sensor {}", event.sensorId());
                } else {
                  log.error("Failed to publish alert for sensor {}", event.sensorId(), ex);
                }
              });
    } catch (KafkaException | org.apache.kafka.common.KafkaException ex) {
      log.error("Failed to publish alert for sensor {}", event.sensorId(), ex);
    }
  }

  @Override
  public void destroy() {
    alertExecutor.shutdownNow();
  }
}
