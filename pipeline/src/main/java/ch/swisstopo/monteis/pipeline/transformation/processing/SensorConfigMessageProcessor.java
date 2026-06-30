package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigMessageProcessor {

  private static final Logger log = LoggerFactory.getLogger(SensorConfigMessageProcessor.class);

  /**
   * Wraps the business logic with Kafka safety checks, tombstone handling, and poison pill protection.
   */
  public void processSafely(
      SensorConfig sensorConfig,
      String sensorId,
      Acknowledgment ack,
      Consumer<SensorConfig> businessLogic) {

    if (sensorConfig == null) {
      log.info("Received tombstone for {}. Stopping processing.", sensorId);
      ack.acknowledge();
      return;
    }

    if (sensorConfig.getSensorId() == null) {
      log.error("Received SensorConfig with null ID! Cannot process. Payload: {}", sensorConfig);
      ack.acknowledge();
      return;
    }

    try {
      businessLogic.accept(sensorConfig);
    } catch (IllegalArgumentException e) {
      // POISON PILL PROTECTION
      log.error(
          "CRITICAL: Failed to process config for Sensor {}. Formula: '{}'. Reason: {}",
          sensorConfig.getSensorId(),
          sensorConfig.getFormula(),
          e.getMessage());
    }

    ack.acknowledge();
  }
}
