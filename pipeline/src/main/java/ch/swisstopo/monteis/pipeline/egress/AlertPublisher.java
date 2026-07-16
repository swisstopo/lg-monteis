package ch.swisstopo.monteis.pipeline.egress;

import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertPublisher {
  private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

  private final KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;
  private final String alertTopic;

  public AlertPublisher(
      KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate,
      @Value("${pipeline.kafka.topics.alerts:sensor-alerts}") String alertTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.alertTopic = alertTopic;
  }

  /**
   * Listens for the internal Spring Event and publishes it to the sensor-alerts topic.
   */
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
    kafkaTemplate
        .send(alertTopic, event.sensorId(), event)
        .whenComplete(
            (result, ex) -> {
              if (ex == null) {
                log.debug("Successfully published alert for sensor {}", event.sensorId());
              } else {
                log.error("Failed to publish alert for sensor {}", event.sensorId(), ex);
                // Depending on requirements, you might want to implement a retry mechanism here
              }
            });
  }
}
