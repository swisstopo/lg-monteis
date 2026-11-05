package ch.swisstopo.monteis.pipeline.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.config.AlertingConfig;
import ch.swisstopo.monteis.pipeline.egress.AlertPublisher;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.processing.SensorDataBatchProcessor;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.SensorConfigCache;
import ch.swisstopo.monteis.pipeline.transformation.standardization.SIStandardizer;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundsValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class BoundBreachAlertingFailureTest {

  private static final String DAS_KEY = "SOL_EXPERTS__a3265_BCL_11_PKP_01__value";
  private static final Duration NON_BLOCKING = Duration.ofSeconds(2);

  private KafkaTemplate<String, SensorBoundBreachedEvent> kafkaTemplate;
  private SensorConfigCache sensorConfigCache;
  private SensorReadingRepository sensorReadingRepository;
  private TransactionTemplate transactionTemplate;
  private Acknowledgment ack;
  private ApplicationContextRunner contextRunner;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    sensorConfigCache = mock(SensorConfigCache.class);
    sensorReadingRepository = mock(SensorReadingRepository.class);
    transactionTemplate = mock(TransactionTemplate.class);
    ack = mock(Acknowledgment.class);

    willAnswer(
            invocation -> {
              Consumer<TransactionStatus> action = invocation.getArgument(0);
              action.accept(null);
              return null;
            })
        .given(transactionTemplate)
        .executeWithoutResult(any());

    given(sensorConfigCache.getActiveConfig(DAS_KEY))
        .willReturn(
            new ActiveSensorConfig(
                new SensorParameterConfig(
                    Das.SOL_EXPERTS,
                    "a3265_BCL_11_PKP_01",
                    "value",
                    UUID.randomUUID(),
                    "x - 1",
                    103.0,
                    0.0,
                    6)));

    contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(AlertingConfig.class)
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(SIStandardizer.class)
            .withBean(BoundsValidator.class)
            .withBean(TransformationOrchestrator.class)
            .withBean(
                "alertPublisher",
                AlertPublisher.class,
                () -> new AlertPublisher(kafkaTemplate, "sensor-alerts"));
  }

  @Test
  void should_store_and_ack_breaching_reading_when_alert_send_throws_synchronously() {
    // given
    given(kafkaTemplate.send(eq("sensor-alerts"), anyString(), any(SensorBoundBreachedEvent.class)))
        .willThrow(new KafkaException("Topic sensor-alerts not present in metadata"));

    contextRunner.run(
        context -> {
          // when
          assertTimeoutPreemptively(NON_BLOCKING, () -> processBreachingReading(context));

          // then
          assertBreachingReadingStoredAndAcked();
          then(kafkaTemplate).should(timeout(1000)).send(eq("sensor-alerts"), anyString(), any());
        });
  }

  @Test
  void should_store_and_ack_breaching_reading_while_alert_send_blocks() {
    // given
    CountDownLatch releaseSend = new CountDownLatch(1);
    given(kafkaTemplate.send(eq("sensor-alerts"), anyString(), any(SensorBoundBreachedEvent.class)))
        .willAnswer(
            invocation -> {
              releaseSend.await(5, TimeUnit.SECONDS);
              throw new KafkaException("Send failed");
            });

    contextRunner.run(
        context -> {
          try {
            // when
            assertTimeoutPreemptively(NON_BLOCKING, () -> processBreachingReading(context));

            // then
            assertBreachingReadingStoredAndAcked();
          } finally {
            releaseSend.countDown();
          }
        });
  }

  private void processBreachingReading(ApplicationContext context) {
    SensorDataBatchProcessor processor =
        new SensorDataBatchProcessor(
            sensorConfigCache,
            context.getBean(TransformationOrchestrator.class),
            sensorReadingRepository,
            transactionTemplate,
            new SimpleMeterRegistry());

    processor.processAndPersist(
        List.of(new NormalizedSensorData(DAS_KEY, "1758527700000", 3059.774)),
        List.of(System.currentTimeMillis()),
        ack);
  }

  @SuppressWarnings("unchecked")
  private void assertBreachingReadingStoredAndAcked() {
    ArgumentCaptor<List<SensorReadingRecord>> stored = ArgumentCaptor.forClass(List.class);
    then(sensorReadingRepository).should().upsertBatch(stored.capture());
    assertThat(stored.getValue())
        .singleElement()
        .satisfies(
            reading -> {
              assertThat(reading.getRawValue()).isEqualTo(3059.774);
              assertThat(reading.getStatus()).isEqualTo(RangeCategory.too_high);
            });
    then(ack).should().acknowledge();
  }
}
