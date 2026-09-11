package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(SensorConfigMessageHandler.class);

  /**
   * Wraps the business logic with Kafka safety checks, tombstone handling, and poison pill protection.
   */
  public void processSafely(
      SensorParameterConfig sensorConfig,
      String dasKey,
      Acknowledgment ack,
      Consumer<SensorParameterConfig> businessLogic) {

    if (sensorConfig == null) {
      log.info("Received tombstone for {}. Stopping processing.", dasKey);
      ack.acknowledge();
      return;
    }

    if (sensorConfig.getSensorParameterId() == null) {
      log.error(
          "Received SensorParameterConfig with null sensor_parameter_id! Cannot process. Payload:"
              + " {}",
          sensorConfig);
      ack.acknowledge();
      return;
    }

    try {
      businessLogic.accept(sensorConfig);
    } catch (IllegalArgumentException e) {
      // POISON PILL PROTECTION
      log.error(
          "CRITICAL: Failed to process config for DAS key {}. Formula: '{}'. Reason: {}",
          dasKey,
          sensorConfig.getFormula(),
          e.getMessage());
    }

    ack.acknowledge();
  }
}
