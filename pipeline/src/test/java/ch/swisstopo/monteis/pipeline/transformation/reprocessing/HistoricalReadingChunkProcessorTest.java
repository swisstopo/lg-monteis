package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.ChunkProcessingResult;
import ch.swisstopo.monteis.pipeline.transformation.ProcessingOrigin;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class HistoricalReadingChunkProcessorTest {

  @Mock private SensorReadingRepository sensorReadingRepository;

  @Mock private TransformationOrchestrator transformationOrchestrator;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private TransactionTemplate transactionTemplate;

  @Captor private ArgumentCaptor<List<SensorReadingRecord>> dbRecordsCaptor;

  private HistoricalReadingChunkProcessor chunkService;

  private SimpleMeterRegistry meterRegistry;

  private final int testChunkSize = 100;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    chunkService =
        new HistoricalReadingChunkProcessor(
            sensorReadingRepository,
            transformationOrchestrator,
            transactionTemplate,
            testChunkSize,
            meterRegistry);

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

  private static SensorParameterConfig config(UUID sensorParameterId, int version) {
    return new SensorParameterConfig(
        Das.SOL_EXPERTS, "deviceA", "temperature", sensorParameterId, "x + 1", 100.0, 0.0, version);
  }

  @Test
  void should_return_zero_when_no_old_records_found_by_id() {
    // given
    UUID sensorParameterId = UUID.randomUUID();
    ActiveSensorConfig config = new ActiveSensorConfig(config(sensorParameterId, (short) 2));

    given(
            sensorReadingRepository.fetchOldSensorDataById(
                sensorParameterId, (short) 2, testChunkSize, null))
        .willReturn(List.of());

    // when
    ChunkProcessingResult result = chunkService.processNextChunkById(config, null);

    // then
    assertThat(result.processedCount()).isZero();
    assertThat(result.cursor()).isNull();
    then(transformationOrchestrator).shouldHaveNoInteractions();
    then(sensorReadingRepository).should(never()).bulkUpdate(any());
  }

  @Test
  void should_process_and_bulk_update_valid_records_matched_by_id() throws TransformationException {
    // given
    UUID sensorParameterId = UUID.randomUUID();
    ActiveSensorConfig config = new ActiveSensorConfig(config(sensorParameterId, (short) 2));
    OffsetDateTime timestamp = OffsetDateTime.now();

    SensorReadingRecord oldRecord =
        new SensorReadingRecord(
            timestamp, "deviceA", 10.0, 15.0, (short) 1, RangeCategory.correct, sensorParameterId);

    given(
            sensorReadingRepository.fetchOldSensorDataById(
                sensorParameterId, (short) 2, testChunkSize, null))
        .willReturn(List.of(oldRecord));

    SensorReadingRecord transformedRecord =
        new SensorReadingRecord(
            timestamp, "deviceA", 10.0, 20.0, (short) 2, RangeCategory.correct, sensorParameterId);

    given(
            transformationOrchestrator.transform(
                "deviceA", 10.0, timestamp, config, ProcessingOrigin.REPROCESS))
        .willReturn(transformedRecord);

    // when
    ChunkProcessingResult result = chunkService.processNextChunkById(config, null);

    // then
    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(result.cursor()).isEqualTo(timestamp);

    then(sensorReadingRepository).should().bulkUpdate(dbRecordsCaptor.capture());
    assertThat(dbRecordsCaptor.getValue()).containsExactly(transformedRecord);

    assertThat(
            meterRegistry
                .get("pipeline.reprocessing.records")
                .tag("result", "success")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get("pipeline.reprocessing.records")
                .tag("result", "poison_pill")
                .counter()
                .count())
        .isZero();
  }

  @Test
  void should_process_unbackfilled_records_matched_by_das_key() throws TransformationException {
    // given
    UUID sensorParameterId = UUID.randomUUID();
    ActiveSensorConfig config = new ActiveSensorConfig(config(sensorParameterId, (short) 1));
    OffsetDateTime timestamp = OffsetDateTime.now();
    String dasKey = "SOL_EXPERTS__deviceA__temperature";

    // sensor_parameter_id still null on the fetched row - that's why it matched this backlog
    SensorReadingRecord oldRecord =
        new SensorReadingRecord(
            timestamp, dasKey, 10.0, 15.0, (short) 0, RangeCategory.correct, null);

    given(
            sensorReadingRepository.fetchOldSensorDataByDasKeyUnbackfilled(
                dasKey, (short) 1, testChunkSize, null))
        .willReturn(List.of(oldRecord));

    SensorReadingRecord transformedRecord =
        new SensorReadingRecord(
            timestamp, dasKey, 10.0, 20.0, (short) 1, RangeCategory.correct, sensorParameterId);

    given(
            transformationOrchestrator.transform(
                dasKey, 10.0, timestamp, config, ProcessingOrigin.REPROCESS))
        .willReturn(transformedRecord);

    // when
    ChunkProcessingResult result =
        chunkService.processNextChunkByDasKeyUnbackfilled(config, dasKey, null);

    // then
    assertThat(result.processedCount()).isEqualTo(1);
    then(sensorReadingRepository).should().bulkUpdate(dbRecordsCaptor.capture());
    assertThat(dbRecordsCaptor.getValue()).containsExactly(transformedRecord);
    assertThat(dbRecordsCaptor.getValue().getFirst().getSensorParameterId())
        .isEqualTo(sensorParameterId);
  }

  @Test
  void should_handle_poison_pills_by_bumping_version_and_setting_null_norm_value()
      throws TransformationException {
    // given
    UUID sensorParameterId = UUID.randomUUID();
    ActiveSensorConfig config = new ActiveSensorConfig(config(sensorParameterId, (short) 3));
    OffsetDateTime timestamp = OffsetDateTime.now();

    // The old record has version 1 and a raw value that will cause math to fail
    SensorReadingRecord poisonRecord =
        new SensorReadingRecord(
            timestamp, "deviceB", -999.0, 5.0, (short) 1, RangeCategory.correct, sensorParameterId);

    given(
            sensorReadingRepository.fetchOldSensorDataById(
                sensorParameterId, (short) 3, testChunkSize, null))
        .willReturn(List.of(poisonRecord));

    TransformationException ex = mock(TransformationException.class);
    given(ex.getMessage()).willReturn("Math calculation failed");

    given(
            transformationOrchestrator.transform(
                "deviceB", -999.0, timestamp, config, ProcessingOrigin.REPROCESS))
        .willThrow(ex);

    // when
    ChunkProcessingResult result = chunkService.processNextChunkById(config, null);

    // then
    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(result.cursor()).isEqualTo(timestamp);

    then(sensorReadingRepository).should().bulkUpdate(dbRecordsCaptor.capture());

    SensorReadingRecord savedRecord = dbRecordsCaptor.getValue().getFirst();

    // CRITICAL: Verify the loop-breaking mechanics applied during the catch block
    assertThat(savedRecord.getVersion()).isEqualTo((short) 3);
    assertThat(savedRecord.getNormValue()).isNull();
    assertThat(savedRecord.getRawValue()).isEqualTo(-999.0);
    // The id must still be backfilled even on a poison pill, or this row would be stuck forever
    assertThat(savedRecord.getSensorParameterId()).isEqualTo(sensorParameterId);

    assertThat(
            meterRegistry
                .get("pipeline.reprocessing.records")
                .tag("result", "success")
                .counter()
                .count())
        .isZero();
    assertThat(
            meterRegistry
                .get("pipeline.reprocessing.records")
                .tag("result", "poison_pill")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
