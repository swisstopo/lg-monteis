package ch.swisstopo.monteis.pipeline.ingress.external.solexperts;

import ch.swisstopo.monteis.pipeline.ingress.external.VendorDataNormalizer;
import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

@Component
public class RawSolExpertsSensorDataListener {

  private static final Logger log = LoggerFactory.getLogger(RawSolExpertsSensorDataListener.class);

  private final VendorDataNormalizer<RawSolExpertsSensorData> normalizer;

  public RawSolExpertsSensorDataListener(VendorDataNormalizer<RawSolExpertsSensorData> normalizer) {
    this.normalizer = normalizer;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.raw-solexperts}",
      groupId = "solexperts-adapter-group",
      concurrency = "${app.kafka.concurrency:6}",
      containerFactory = "forwardingMessageFactory")
  @SendTo("${app.kafka.topics.normalized}")
  public List<Message<NormalizedSensorData>> processRawMessage(RawSolExpertsSensorData rawPayload) {
    log.info("Received raw SolExperts message: {}", rawPayload);

    // Spring Kafka intercepts the returned List, splits it, and publishes each message to
    // internal-normalized
    return normalizer.normalizeToMessages(rawPayload);
  }
}
