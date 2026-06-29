package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class SensorConfigReprocessingListener {

  private static final Logger log = LoggerFactory.getLogger(SensorConfigReprocessingListener.class);

  private final ReprocessService reprocessService;

  public SensorConfigReprocessingListener(ReprocessService reprocessService) {
    this.reprocessService = reprocessService;
  }

  @KafkaListener(topics = "${app.kafka.topics.sensor-config}", groupId = "reprocessing-group")
  public void consumeSensorConfigUpdate(SensorConfig sensorConfig, Acknowledgment ack) {
    log.info(
        "Received sensor config update for Sensor {}. Triggering reprocessing flow.",
        sensorConfig.getSensorId());

    try {
      ActiveSensorConfig activeConfig = new ActiveSensorConfig(sensorConfig);

      reprocessService.checkAndReprocessHistoricalData(activeConfig);

    } catch (IllegalArgumentException e) {
      // 3. POISON PILL PROTECTION
      // If the formula is completely invalid, Parsington throws an exception during creation.
      // We log it and let it fall through to the ACK so the partition keeps moving.
      log.error(
          "CRITICAL: Failed to parse formula for Sensor {}. Formula: '{}'. Reprocessing aborted."
              + " Reason: {}",
          sensorConfig.getSensorId(),
          sensorConfig.getFormula(),
          e.getMessage());
    }

    // Acknowledge the message ONLY after all chunks have been safely processed,
    // or if the configuration formula itself was permanently invalid.
    ack.acknowledge();
    log.debug(
        "Successfully acknowledged sensor config update for Sensor {}", sensorConfig.getSensorId());
  }
}
