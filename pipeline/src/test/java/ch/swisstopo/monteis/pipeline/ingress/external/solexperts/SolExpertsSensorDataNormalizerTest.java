package ch.swisstopo.monteis.pipeline.ingress.external.solexperts;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

class SolExpertsSensorDataNormalizerTest {

  private final SolExpertsSensorDataNormalizer normalizer = new SolExpertsSensorDataNormalizer();

  @Test
  void should_return_empty_list_when_values_are_empty() {
    // given
    RawSolExpertsSensorData emptyPayload =
        new RawSolExpertsSensorData("device1", "2026-06-23T09:00:00Z", Collections.emptyMap());

    // when
    List<Message<NormalizedSensorData>> result = normalizer.normalizeToMessages(emptyPayload);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_list_when_values_are_null() {
    // given
    RawSolExpertsSensorData nullPayload =
        new RawSolExpertsSensorData("device1", "2026-06-23T09:00:00Z", null);

    // when
    List<Message<NormalizedSensorData>> result = normalizer.normalizeToMessages(nullPayload);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_list_when_payload_is_null() {
    // when
    List<Message<NormalizedSensorData>> result = normalizer.normalizeToMessages(null);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void should_map_data_and_attach_kafka_key_headers_on_success() {
    // given
    Map<String, Double> values =
        Map.of(
            "value", 15.5,
            "battery", 99.0);
    RawSolExpertsSensorData payload =
        new RawSolExpertsSensorData("deviceA", "2026-06-23T09:00:00Z", values);

    // when
    List<Message<NormalizedSensorData>> result = normalizer.normalizeToMessages(payload);

    // then
    assertThat(result).hasSize(2);

    // Verify main value (uses base deviceName as key)
    Message<NormalizedSensorData> mainMessage =
        result.stream()
            .filter(m -> "deviceA".equals(m.getHeaders().get(KafkaHeaders.KEY)))
            .findFirst()
            .orElseThrow();
    assertThat(mainMessage.getPayload())
        .isEqualTo(new NormalizedSensorData("deviceA", "2026-06-23T09:00:00Z", 15.5));

    // Verify battery value (appends key to deviceName as key)
    Message<NormalizedSensorData> batteryMessage =
        result.stream()
            .filter(m -> "deviceA_battery".equals(m.getHeaders().get(KafkaHeaders.KEY)))
            .findFirst()
            .orElseThrow();
    assertThat(batteryMessage.getPayload())
        .isEqualTo(new NormalizedSensorData("deviceA_battery", "2026-06-23T09:00:00Z", 99.0));
  }
}
