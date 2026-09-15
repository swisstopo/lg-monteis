package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.ChunkProcessingResult;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorReprocessingOrchestratorTest {

  @Mock private SensorReadingRepository sensorReadingRepository;

  @Mock private HistoricalReadingChunkProcessor chunkService;

  private SimpleMeterRegistry meterRegistry;

  private SensorReprocessingOrchestrator sensorReprocessingOrchestrator;

  private static final String DAS_KEY = "SOL_EXPERTS__deviceA__temperature";

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    sensorReprocessingOrchestrator =
        new SensorReprocessingOrchestrator(sensorReadingRepository, chunkService, meterRegistry);
  }

  private static ActiveSensorConfig config(UUID sensorParameterId, int version) {
    return new ActiveSensorConfig(
        new SensorParameterConfig(
            Das.SOL_EXPERTS,
            "deviceA",
            "temperature",
            sensorParameterId,
            "x + 1",
            100.0,
            0.0,
            version));
  }

  @Test
  void should_skip_reprocessing_when_neither_branch_has_outdated_records() {
    // given
    UUID sensorParameterId = UUID.randomUUID();
    ActiveSensorConfig config = config(sensorParameterId, (short) 2);

    given(sensorReadingRepository.checkOldSensorDataById(sensorParameterId, (short) 2))
        .willReturn(false);
    given(sensorReadingRepository.checkOldSensorDataByDasKeyUnbackfilled(DAS_KEY, (short) 2))
        .willReturn(false);

    // when
    sensorReprocessingOrchestrator.checkAndReprocessHistoricalData(config);

    // then
    then(chunkService).shouldHaveNoInteractions();
    assertThat(meterRegistry.get("pipeline.reprocessing.run.duration").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_reprocess_id_matched_backlog_in_chunks_until_no_records_left() {
    // given
    UUID sensorParameterId = UUID.randomUUID();
    ActiveSensorConfig config = config(sensorParameterId, (short) 3);

    given(sensorReadingRepository.checkOldSensorDataById(sensorParameterId, (short) 3))
        .willReturn(true);
    given(sensorReadingRepository.checkOldSensorDataByDasKeyUnbackfilled(DAS_KEY, (short) 3))
        .willReturn(false);

    OffsetDateTime firstCursor = OffsetDateTime.now().minusMinutes(1);
    OffsetDateTime secondCursor = OffsetDateTime.now().minusMinutes(2);

    given(chunkService.processNextChunkById(eq(config), any()))
        .willReturn(
            new ChunkProcessingResult(100, firstCursor),
            new ChunkProcessingResult(50, secondCursor),
            new ChunkProcessingResult(0, secondCursor));

    // when
    sensorReprocessingOrchestrator.checkAndReprocessHistoricalData(config);

    // then
    then(chunkService).should(times(3)).processNextChunkById(eq(config), any());
    then(chunkService).should().processNextChunkById(config, null);
    then(chunkService).should().processNextChunkById(config, firstCursor);
    then(chunkService).should().processNextChunkById(config, secondCursor);
    then(chunkService).should(never()).processNextChunkByDasKeyUnbackfilled(any(), any(), any());

    assertThat(meterRegistry.get("pipeline.reprocessing.run.duration").timer().count())
        .isEqualTo(1);
  }

  @Test
  void should_reprocess_das_key_unbackfilled_backlog_in_chunks_until_no_records_left() {
    // given
    UUID sensorParameterId = UUID.randomUUID();
    ActiveSensorConfig config = config(sensorParameterId, (short) 1);

    given(sensorReadingRepository.checkOldSensorDataById(sensorParameterId, (short) 1))
        .willReturn(false);
    given(sensorReadingRepository.checkOldSensorDataByDasKeyUnbackfilled(DAS_KEY, (short) 1))
        .willReturn(true);

    OffsetDateTime firstCursor = OffsetDateTime.now().minusMinutes(1);

    given(chunkService.processNextChunkByDasKeyUnbackfilled(eq(config), eq(DAS_KEY), any()))
        .willReturn(
            new ChunkProcessingResult(20, firstCursor), new ChunkProcessingResult(0, firstCursor));

    // when
    sensorReprocessingOrchestrator.checkAndReprocessHistoricalData(config);

    // then
    then(chunkService)
        .should(times(2))
        .processNextChunkByDasKeyUnbackfilled(eq(config), eq(DAS_KEY), any());
    then(chunkService).should().processNextChunkByDasKeyUnbackfilled(config, DAS_KEY, null);
    then(chunkService).should().processNextChunkByDasKeyUnbackfilled(config, DAS_KEY, firstCursor);
    then(chunkService).should(never()).processNextChunkById(any(), any());

    assertThat(meterRegistry.get("pipeline.reprocessing.run.duration").timer().count())
        .isEqualTo(1);
  }
}
