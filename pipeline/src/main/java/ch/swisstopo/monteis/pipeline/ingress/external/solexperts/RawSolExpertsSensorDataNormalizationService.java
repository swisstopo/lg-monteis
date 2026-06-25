package ch.swisstopo.monteis.pipeline.ingress.external.solexperts;

import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorData;
import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorDataPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class RawSolExpertsSensorDataNormalizationService {
  private static final Logger log =
      LoggerFactory.getLogger(RawSolExpertsSensorDataNormalizationService.class);

  private final NormalizedSensorDataPublisher publisher;

  public RawSolExpertsSensorDataNormalizationService(NormalizedSensorDataPublisher publisher) {
    this.publisher = publisher;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.raw-solexperts}",
      groupId = "solexperts-adapter-group",
      concurrency = "${app.kafka.concurrency:6}")
  public void processRawMessage(RawSolExpertsSensorData rawPayload, Acknowledgment ack) {
    log.info("Received raw SolExperts message: {}", rawPayload);

    if (rawPayload.values() == null || rawPayload.values().isEmpty()) {
      ack.acknowledge();
      return;
    }

    try {
      List<CompletableFuture<?>> sendFutures = new ArrayList<>();

      for (Map.Entry<String, Double> entry : rawPayload.values().entrySet()) {
        String newDeviceName =
            entry.getKey().equals("value")
                ? rawPayload.deviceName()
                : rawPayload.deviceName() + "_" + entry.getKey();

        NormalizedSensorData canonicalPayload =
            new NormalizedSensorData(newDeviceName, rawPayload.ts(), entry.getValue());

        // Send the data and capture the Future
        sendFutures.add(publisher.publish(newDeviceName, canonicalPayload));
      }

      // WAIT for all messages to be safely persisted in the internal-normalized topic
      CompletableFuture.allOf(sendFutures.toArray(new CompletableFuture[0])).join();

      // ONLY acknowledge the raw message after the internal broker confirms receipt!
      ack.acknowledge();

    } catch (Exception e) {
      // If the send fails, do NOT acknowledge.
      // Throw the exception so Kafka hands the raw message back to you to try again.
      throw new RuntimeException("Failed to forward normalized payload", e);
    }
  }
}
