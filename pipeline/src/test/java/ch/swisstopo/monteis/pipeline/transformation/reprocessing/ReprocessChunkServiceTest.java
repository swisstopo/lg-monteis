package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReprocessChunkServiceTest {

  @Mock private SensorReadingRepository sensorReadingRepository;

  @Mock private TransformationOrchestrator transformationOrchestrator;

  @Captor private ArgumentCaptor<List<SensorReadingRecord>> dbRecordsCaptor;

  private ReprocessChunkService chunkService;

  private final int testChunkSize = 100;

  @BeforeEach
  void setUp() {
    chunkService =
        new ReprocessChunkService(
            sensorReadingRepository, transformationOrchestrator, testChunkSize);
  }

  @Test
  void should_return_zero_when_no_old_records_found() {
    // given
    SensorConfig config = new SensorConfig("deviceA", Map.of(), 100.0, 0.0, 2);

    given(sensorReadingRepository.fetchOldSensorData(config, testChunkSize)).willReturn(List.of());

    // when
    int processedCount = chunkService.processNextChunk(config);

    // then
    assertThat(processedCount).isZero();
    then(transformationOrchestrator).shouldHaveNoInteractions();
    then(sensorReadingRepository).should(never()).bulkUpdate(any());
  }

  @Test
  void should_process_and_bulk_update_valid_records() throws TransformationException {
    // given
    SensorConfig config = new SensorConfig("deviceA", Map.of(), 100.0, 0.0, 2);
    OffsetDateTime timestamp = OffsetDateTime.now();

    SensorReadingRecord oldRecord =
        new SensorReadingRecord(timestamp, "deviceA", 10.0, 15.0, (short) 1, RangeCategory.correct);

    given(sensorReadingRepository.fetchOldSensorData(config, testChunkSize))
        .willReturn(List.of(oldRecord));

    SensorReadingRecord transformedRecord =
        new SensorReadingRecord(timestamp, "deviceA", 10.0, 20.0, (short) 2, RangeCategory.correct);

    given(transformationOrchestrator.transform("deviceA", 10.0, timestamp, config))
        .willReturn(transformedRecord);

    // when
    int processedCount = chunkService.processNextChunk(config);

    // then
    assertThat(processedCount).isEqualTo(1);

    then(sensorReadingRepository).should().bulkUpdate(dbRecordsCaptor.capture());
    assertThat(dbRecordsCaptor.getValue()).containsExactly(transformedRecord);
  }

  @Test
  void should_handle_poison_pills_by_bumping_version_and_setting_null_norm_value()
      throws TransformationException {
    // given
    SensorConfig config = new SensorConfig("deviceB", Map.of(), 50.0, -10.0, 3);
    OffsetDateTime timestamp = OffsetDateTime.now();

    // The old record has version 1 and a raw value that will cause math to fail
    SensorReadingRecord poisonRecord =
        new SensorReadingRecord(
            timestamp, "deviceB", -999.0, 5.0, (short) 1, RangeCategory.correct);

    given(sensorReadingRepository.fetchOldSensorData(config, testChunkSize))
        .willReturn(List.of(poisonRecord));

    TransformationException ex = mock(TransformationException.class);
    given(ex.getMessage()).willReturn("Math calculation failed");

    given(transformationOrchestrator.transform("deviceB", -999.0, timestamp, config)).willThrow(ex);

    // when
    int processedCount = chunkService.processNextChunk(config);

    // then
    assertThat(processedCount).isEqualTo(1);

    then(sensorReadingRepository).should().bulkUpdate(dbRecordsCaptor.capture());

    SensorReadingRecord savedRecord = dbRecordsCaptor.getValue().getFirst();

    // CRITICAL: Verify the loop-breaking mechanics applied during the catch block
    assertThat(savedRecord.getVersion()).isEqualTo((short) 3);
    assertThat(savedRecord.getNormValue()).isNull();
    assertThat(savedRecord.getRawValue()).isEqualTo(-999.0);
  }
}
