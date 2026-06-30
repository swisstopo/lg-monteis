package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.SensorConfigMessageProcessor;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class SensorConfigReprocessingListener {

  private static final Logger log = LoggerFactory.getLogger(SensorConfigReprocessingListener.class);

  private final ReprocessService reprocessService;
  private final SensorConfigMessageProcessor messageProcessor;

  public SensorConfigReprocessingListener(
      ReprocessService reprocessService, SensorConfigMessageProcessor messageProcessor) {
    this.reprocessService = reprocessService;
    this.messageProcessor = messageProcessor;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.sensor-config}",
      groupId = "reprocessing-group",
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
          log.info("Triggering reprocessing flow for Sensor {}.", validConfig.getSensorId());
          ActiveSensorConfig activeConfig = new ActiveSensorConfig(validConfig);
          reprocessService.checkAndReprocessHistoricalData(activeConfig);
        });
  }
}
