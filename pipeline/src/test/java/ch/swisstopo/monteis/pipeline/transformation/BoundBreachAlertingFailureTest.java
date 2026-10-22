package ch.swisstopo.monteis.pipeline.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.timeout;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.egress.AlertPublisher;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.standardization.SIStandardizer;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundsValidator;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class BoundBreachAlertingFailureTest {

  @Mock private KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;

  private AlertPublisher alertPublisher;

  private TransformationOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    alertPublisher = new AlertPublisher(kafkaTemplate, "sensor-alerts", 10);
    ApplicationEventPublisher synchronousEventPublisher =
        event -> alertPublisher.handleBoundBreach((SensorBoundBreachedEvent) event);
    BoundsValidator boundsValidator =
        new BoundsValidator(synchronousEventPublisher, Clock.systemUTC());
    orchestrator = new TransformationOrchestrator(new SIStandardizer(), boundsValidator);
  }

  @AfterEach
  void tearDown() {
    alertPublisher.destroy();
  }

  @Test
  void should_transform_breaching_reading_even_when_alert_send_throws_synchronously() {
    // given
    ActiveSensorConfig activeConfig =
        new ActiveSensorConfig(
            new SensorParameterConfig(
                Das.SOL_EXPERTS,
                "a3265_BCL_11_PKP_01",
                "value",
                UUID.randomUUID(),
                "x - 1",
                103.0,
                0.0,
                6));
    given(kafkaTemplate.send(eq("sensor-alerts"), anyString(), any(SensorBoundBreachedEvent.class)))
        .willThrow(new KafkaException("Topic sensor-alerts not present in metadata"));

    // when
    SensorReadingRecord result =
        orchestrator.transform(
            "SOL_EXPERTS__a3265_BCL_11_PKP_01__value",
            3059.774,
            "1758527700000",
            activeConfig,
            ProcessingOrigin.INGEST);

    // then
    then(kafkaTemplate).should(timeout(1000)).send(eq("sensor-alerts"), anyString(), any());
    assertThat(result.getRawValue()).isEqualTo(3059.774);
    assertThat(result.getStatus()).isEqualTo(RangeCategory.too_high);
  }
}
