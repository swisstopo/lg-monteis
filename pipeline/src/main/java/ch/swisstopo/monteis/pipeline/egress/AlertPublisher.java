package ch.swisstopo.monteis.pipeline.egress;

import ch.swisstopo.monteis.pipeline.config.AlertingConfig;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AlertPublisher implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

  static final int MAX_BLOCK_MS = 5_000;

  private final KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;
  private final String alertTopic;

  @Autowired
  public AlertPublisher(
      ProducerFactory<String, SensorBoundBreachedEvent> producerFactory,
      @Value("${app.kafka.topics.alerts:sensor-alerts}") String alertTopic) {
    this(
        new KafkaTemplate<>(
            producerFactory,
            Map.<String, Object>of(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS)),
        alertTopic);
  }

  public AlertPublisher(
      KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate, String alertTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.alertTopic = alertTopic;
  }

  /** Listens for the internal Spring Event and queues it for publishing to the alert topic. */
  @Async(AlertingConfig.ALERT_EXECUTOR)
  @EventListener
  public void handleBoundBreach(SensorBoundBreachedEvent event) {
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
    } catch (RuntimeException ex) {
      log.error("Failed to publish alert for sensor {}", event.sensorId(), ex);
    }
  }

  @Override
  public void destroy() {
    kafkaTemplate.destroy();
  }
}
