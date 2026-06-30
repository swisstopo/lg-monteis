package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.SensorConfigMessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class SensorConfigCacheHydrator {

  private static final Logger log = LoggerFactory.getLogger(SensorConfigCacheHydrator.class);

  private final SensorConfigCache cacheService;
  private final SensorConfigMessageProcessor messageProcessor;

  public SensorConfigCacheHydrator(
      SensorConfigCache cacheService, SensorConfigMessageProcessor messageProcessor) {
    this.cacheService = cacheService;
    this.messageProcessor = messageProcessor;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.sensor-config}",
      // 1. Generate the unique ID so it broadcasts to all pods
      groupId = "cache-hydrator-#{T(java.util.UUID).randomUUID().toString()}",
      // 2. FORCE Kafka to send everything from the beginning of the topic!
      properties = {"auto.offset.reset=earliest"},
      containerFactory = "singleMessageFactory")
  public void consumeSensorConfigUpdate(
      @Payload(required = false) SensorConfig sensorConfig,
      @Header(KafkaHeaders.RECEIVED_KEY) String sensorId,
      Acknowledgment ack) {

    messageProcessor.processSafely(
        sensorConfig,
        sensorId,
        ack,
        validConfig -> {
          cacheService.updateSensorConfig(validConfig);
          log.info("Pod cache updated for sensor {}", validConfig.getSensorId());
        });
  }
}
