package ch.swisstopo.monteis.pipeline.ingress.external.solexperts;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.anyString;

import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorData;
import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorDataPublisher;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class RawSolExpertsSensorDataNormalizationServiceTest {

  @Mock private NormalizedSensorDataPublisher publisher;

  @Mock private Acknowledgment ack;

  @InjectMocks private RawSolExpertsSensorDataNormalizationService service;

  @Test
  void should_acknowledge_and_return_when_values_are_empty() {
    // given
    RawSolExpertsSensorData emptyPayload =
        new RawSolExpertsSensorData("device1", "2026-06-23T09:00:00Z", Collections.emptyMap());

    // when
    service.processRawMessage(emptyPayload, ack);

    // then
    then(ack).should().acknowledge();
    then(publisher).shouldHaveNoInteractions();
  }

  @Test
  void should_acknowledge_and_return_when_values_are_null() {
    // given
    RawSolExpertsSensorData nullPayload =
        new RawSolExpertsSensorData("device1", "2026-06-23T09:00:00Z", null);

    // when
    service.processRawMessage(nullPayload, ack);

    // then
    then(ack).should().acknowledge();
    then(publisher).shouldHaveNoInteractions();
  }

  @Test
  void should_publish_mapped_data_and_acknowledge_on_success() {
    // given
    Map<String, Double> values =
        Map.of(
            "value", 15.5,
            "battery", 99.0);
    RawSolExpertsSensorData payload =
        new RawSolExpertsSensorData("deviceA", "2026-06-23T09:00:00Z", values);

    CompletableFuture<SendResult<String, NormalizedSensorData>> successFuture =
        CompletableFuture.completedFuture(null);

    given(publisher.publish(anyString(), any(NormalizedSensorData.class)))
        .willReturn(successFuture);

    // when
    service.processRawMessage(payload, ack);

    // then
    NormalizedSensorData expectedMainData =
        new NormalizedSensorData("deviceA", "2026-06-23T09:00:00Z", 15.5);
    then(publisher).should().publish("deviceA", expectedMainData);

    NormalizedSensorData expectedBatteryData =
        new NormalizedSensorData("deviceA_battery", "2026-06-23T09:00:00Z", 99.0);
    then(publisher).should().publish("deviceA_battery", expectedBatteryData);

    then(ack).should().acknowledge();
  }

  @Test
  void should_throw_exception_and_not_acknowledge_on_publish_failure() {
    // given
    Map<String, Double> values = Map.of("value", 15.5);
    RawSolExpertsSensorData payload =
        new RawSolExpertsSensorData("deviceA", "2026-06-23T09:00:00Z", values);

    CompletableFuture<SendResult<String, NormalizedSensorData>> failedFuture =
        new CompletableFuture<>();
    failedFuture.completeExceptionally(new RuntimeException("Kafka Broker is down"));

    given(publisher.publish(anyString(), any(NormalizedSensorData.class))).willReturn(failedFuture);

    // when
    assertThrows(RuntimeException.class, () -> service.processRawMessage(payload, ack));

    // then
    then(ack).shouldHaveNoInteractions();
  }
}
