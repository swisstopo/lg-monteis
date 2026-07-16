package ch.swisstopo.monteis.pipeline.transformation.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.SensorConfigCache;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SensorDataBatchProcessorTest {

  @Mock private SensorConfigCache sensorConfigCache;

  @Mock private TransformationOrchestrator orchestrator;

  @Mock private SensorReadingRepository sensorReadingRepository;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private TransactionTemplate transactionTemplate;

  @Mock private Acknowledgment ack;

  @InjectMocks private SensorDataBatchProcessor sensorDataBatchProcessor;

  @Captor private ArgumentCaptor<List<SensorReadingRecord>> dbRecordsCaptor;

  @BeforeEach
  void setUp() {
    // Instruct the mocked TransactionTemplate to immediately execute the passed lambda!
    willAnswer(
            invocation -> {
              Consumer<TransactionStatus> action = invocation.getArgument(0);
              action.accept(null); // Pass null as TransactionStatus since we don't inspect it
              return null;
            })
        .given(transactionTemplate)
        .executeWithoutResult(any());
  }

  @Test
  void should_process_and_persist_valid_batch_successfully() throws TransformationException {
    // given
    NormalizedSensorData data1 = new NormalizedSensorData("deviceA", "2026-06-23T10:00:00Z", 10.5);
    NormalizedSensorData data2 = new NormalizedSensorData("deviceB", "2026-06-23T10:05:00Z", 20.0);
    List<NormalizedSensorData> batch = List.of(data1, data2);

    ActiveSensorConfig configA =
        new ActiveSensorConfig(new SensorConfig("deviceA", "x + 1", 100.0, 0.0, 1));
    ActiveSensorConfig configB =
        new ActiveSensorConfig(new SensorConfig("deviceB", "x + 2", 50.0, -10.0, 1));

    given(sensorConfigCache.getActiveConfig("deviceA")).willReturn(configA);
    given(sensorConfigCache.getActiveConfig("deviceB")).willReturn(configB);

    SensorReadingRecord record1 = new SensorReadingRecord();
    SensorReadingRecord record2 = new SensorReadingRecord();

    given(orchestrator.transform("deviceA", 10.5, "2026-06-23T10:00:00Z", configA))
        .willReturn(record1);
    given(orchestrator.transform("deviceB", 20.0, "2026-06-23T10:05:00Z", configB))
        .willReturn(record2);

    // when
    sensorDataBatchProcessor.processAndPersist(batch, ack);

    // then
    then(sensorReadingRepository).should().upsertBatch(dbRecordsCaptor.capture());
    assertThat(dbRecordsCaptor.getValue()).containsExactly(record1, record2);

    then(ack).should().acknowledge();
  }

  @Test
  void should_filter_out_poison_pills_and_persist_valid_records() throws TransformationException {
    // given
    NormalizedSensorData validData =
        new NormalizedSensorData("deviceA", "2026-06-23T10:00:00Z", 10.5);
    NormalizedSensorData poisonData =
        new NormalizedSensorData("deviceB", "2026-06-23T10:05:00Z", -999.0);
    List<NormalizedSensorData> batch = List.of(validData, poisonData);

    ActiveSensorConfig configA =
        new ActiveSensorConfig(new SensorConfig("deviceA", "x + 1", 100.0, 0.0, 1));
    ActiveSensorConfig configB =
        new ActiveSensorConfig(new SensorConfig("deviceB", "x + 2", 50.0, -10.0, 1));

    given(sensorConfigCache.getActiveConfig("deviceA")).willReturn(configA);
    given(sensorConfigCache.getActiveConfig("deviceB")).willReturn(configB);

    SensorReadingRecord validRecord = new SensorReadingRecord();
    given(orchestrator.transform("deviceA", 10.5, "2026-06-23T10:00:00Z", configA))
        .willReturn(validRecord);

    // Simulate a TransformationException for the poison pill
    TransformationException poisonException = mock(TransformationException.class);
    given(poisonException.getFailedPayload()).willReturn(-999.0);
    given(poisonException.getMessage()).willReturn("Calculation error: division by zero");

    given(orchestrator.transform("deviceB", -999.0, "2026-06-23T10:05:00Z", configB))
        .willThrow(poisonException);

    // when
    sensorDataBatchProcessor.processAndPersist(batch, ack);

    // then
    then(sensorReadingRepository).should().upsertBatch(dbRecordsCaptor.capture());

    // Assert that ONLY the valid record made it into the database list
    assertThat(dbRecordsCaptor.getValue()).containsExactly(validRecord);

    // Assert that the batch is still acknowledged to prevent infinite retry loops
    then(ack).should().acknowledge();
  }

  @Test
  void should_acknowledge_even_if_entire_batch_fails() throws TransformationException {
    // given
    NormalizedSensorData poisonData =
        new NormalizedSensorData("deviceB", "2026-06-23T10:05:00Z", -999.0);
    List<NormalizedSensorData> batch = List.of(poisonData);

    ActiveSensorConfig configB =
        new ActiveSensorConfig(new SensorConfig("deviceB", "x + 2", 50.0, -10.0, 1));
    given(sensorConfigCache.getActiveConfig("deviceB")).willReturn(configB);

    TransformationException poisonException = mock(TransformationException.class);
    given(orchestrator.transform("deviceB", -999.0, "2026-06-23T10:05:00Z", configB))
        .willThrow(poisonException);

    // when
    sensorDataBatchProcessor.processAndPersist(batch, ack);

    // then
    then(sensorReadingRepository).should().upsertBatch(dbRecordsCaptor.capture());

    assertThat(dbRecordsCaptor.getValue()).isEmpty();

    then(ack).should().acknowledge();
  }
}
