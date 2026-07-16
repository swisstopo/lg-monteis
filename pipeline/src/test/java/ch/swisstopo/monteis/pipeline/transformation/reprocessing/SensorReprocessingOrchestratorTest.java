package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorReprocessingOrchestratorTest {

  @Mock private SensorReadingRepository sensorReadingRepository;

  @Mock private HistoricalReadingChunkProcessor chunkService;

  @InjectMocks private SensorReprocessingOrchestrator sensorReprocessingOrchestrator;

  @Test
  void should_skip_reprocessing_when_no_outdated_records_found() {
    // given
    ActiveSensorConfig config =
        new ActiveSensorConfig(new SensorConfig("deviceA", "x + 1", 100.0, 0.0, 2));

    given(sensorReadingRepository.checkOldSensorData(config.getConfig())).willReturn(false);

    // when
    sensorReprocessingOrchestrator.checkAndReprocessHistoricalData(config);

    // then
    then(sensorReadingRepository).should().checkOldSensorData(config.getConfig());
    then(chunkService).shouldHaveNoInteractions();
  }

  @Test
  void should_reprocess_in_chunks_until_no_records_left() {
    // given
    ActiveSensorConfig config =
        new ActiveSensorConfig(new SensorConfig("deviceB", "x + 2", 100.0, 0.0, 3));

    given(sensorReadingRepository.checkOldSensorData(config.getConfig())).willReturn(true);

    // Simulate returning 100 records on the first call, 50 on the second, and 0 on the third
    given(chunkService.processNextChunk(config)).willReturn(100, 50, 0);

    // when
    sensorReprocessingOrchestrator.checkAndReprocessHistoricalData(config);

    // then
    then(sensorReadingRepository).should().checkOldSensorData(config.getConfig());

    // The do-while loop should have executed exactly 3 times before terminating
    then(chunkService).should(times(3)).processNextChunk(config);
  }
}
