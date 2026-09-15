package ch.swisstopo.monteis.pipeline.ingress.external.solexperts;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.pipeline.ingress.external.VendorDataNormalizer;
import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class SolExpertsSensorDataNormalizer
    implements VendorDataNormalizer<RawSolExpertsSensorData> {

  private static final Logger log = LoggerFactory.getLogger(SolExpertsSensorDataNormalizer.class);

  private static final Das DAS = Das.SOL_EXPERTS;

  @Override
  public List<Message<NormalizedSensorData>> normalizeToMessages(
      RawSolExpertsSensorData rawPayload) {
    if (rawPayload == null || rawPayload.values() == null || rawPayload.values().isEmpty()) {
      return List.of();
    }

    List<Message<NormalizedSensorData>> outboundMessages = new ArrayList<>();

    for (Map.Entry<String, Object> entry : rawPayload.values().entrySet()) {
      if (!(entry.getValue() instanceof Number numericValue)) {
        log.warn(
            "Skipping non-numeric entry '{}'={} in raw SolExperts message: {}",
            entry.getKey(),
            entry.getValue(),
            rawPayload);
        continue;
      }

      // Composite DAS ingest key: <DAS>__<das_sensor_alias>__<das_parameter_alias>.
      // das_sensor_alias
      // is the raw deviceName, das_parameter_alias is the field name within the values map - this
      // must match exactly what SensorConfigPublisher publishes as the Kafka key on
      // internal-sensor-config, and what SensorConfigCache is keyed by, for the same sensor
      // parameter.
      String dasKey = DasKey.compose(DAS, rawPayload.deviceName(), entry.getKey());

      NormalizedSensorData canonicalPayload =
          new NormalizedSensorData(dasKey, rawPayload.ts(), numericValue.doubleValue());

      // Wrap in a Spring Message and attach the routing key for Kafka partitioning
      Message<NormalizedSensorData> message =
          MessageBuilder.withPayload(canonicalPayload).setHeader(KafkaHeaders.KEY, dasKey).build();

      outboundMessages.add(message);
    }

    return outboundMessages;
  }
}
