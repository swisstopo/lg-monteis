package ch.swisstopo.monteis.pipeline.ingress.external;

import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorData;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

public abstract class AbstractNormalizationAdapter {

  private KafkaTemplate<String, NormalizedSensorData> kafkaTemplate;

  private String normalizedTopic;

  @Autowired
  public void setKafkaTemplate(KafkaTemplate<String, NormalizedSensorData> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Value("${app.kafka.topics.normalized}")
  public void setNormalizedTopic(String normalizedTopic) {
    this.normalizedTopic = normalizedTopic;
  }

  protected final CompletableFuture<SendResult<String, NormalizedSensorData>> publishNormalizedData(
      String sensorId, NormalizedSensorData data) {
    return kafkaTemplate.send(normalizedTopic, sensorId, data);
  }
}
