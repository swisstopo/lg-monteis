package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigCacheHydrator {

  private static final Logger log = LoggerFactory.getLogger(SensorConfigCacheHydrator.class);

  private final SensorConfigCache cacheService;

  public SensorConfigCacheHydrator(SensorConfigCache cacheService) {
    this.cacheService = cacheService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.sensor-config}",
      // 1. Generate the unique ID so it broadcasts to all pods
      groupId = "cache-hydrator-#{T(java.util.UUID).randomUUID().toString()}",
      // 2. FORCE Kafka to send everything from the beginning of the topic!
      properties = {"auto.offset.reset=earliest"})
  public void consumeSensorConfigUpdate(SensorConfig sensorConfig, Acknowledgment ack) {
    cacheService.updateSensorConfig(sensorConfig);
    log.info("Pod cache updated for sensor {}", sensorConfig.getSensorId());

    ack.acknowledge();
  }
}
