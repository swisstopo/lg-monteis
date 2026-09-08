package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class SensorDataBatchListener {

  private static final Logger log = LoggerFactory.getLogger(SensorDataBatchListener.class);

  private final SensorDataBatchProcessor sensorDataBatchProcessor;

  public SensorDataBatchListener(SensorDataBatchProcessor sensorDataBatchProcessor) {
    this.sensorDataBatchProcessor = sensorDataBatchProcessor;
  }

  // Read in batches of 200, using 6 concurrent threads inside this single pod
  @KafkaListener(
      topics = "${app.kafka.topics.normalized}",
      groupId = "pipeline-master-group",
      concurrency = "${app.kafka.concurrency:6}",
      containerFactory = "manualAckFactory")
  public void consumeNormalizedSensorData(
      List<NormalizedSensorData> batch,
      @Header(KafkaHeaders.RECEIVED_TIMESTAMP) List<Long> receivedTimestamps,
      Acknowledgment ack) {
    log.info("Received normalized sensor data");
    sensorDataBatchProcessor.processAndPersist(batch, receivedTimestamps, ack);
  }
}
