package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReprocessServiceTest {

  @Mock private SensorReadingRepository sensorReadingRepository;

  @Mock private ReprocessChunkService chunkService;

  @InjectMocks private ReprocessService reprocessService;

  @Test
  void should_skip_reprocessing_when_no_outdated_records_found() {
    // given
    SensorConfig config = new SensorConfig("deviceA", Map.of(), 100.0, 0.0, 2);

    given(sensorReadingRepository.checkOldSensorData(config)).willReturn(false);

    // when
    reprocessService.checkAndReprocessHistoricalData(config);

    // then
    then(sensorReadingRepository).should().checkOldSensorData(config);
    then(chunkService).shouldHaveNoInteractions();
  }

  @Test
  void should_reprocess_in_chunks_until_no_records_left() {
    // given
    SensorConfig config = new SensorConfig("deviceB", Map.of(), 100.0, 0.0, 3);

    given(sensorReadingRepository.checkOldSensorData(config)).willReturn(true);

    // Simulate returning 100 records on the first call, 50 on the second, and 0 on the third
    given(chunkService.processNextChunk(config)).willReturn(100, 50, 0);

    // when
    reprocessService.checkAndReprocessHistoricalData(config);

    // then
    then(sensorReadingRepository).should().checkOldSensorData(config);

    // The do-while loop should have executed exactly 3 times before terminating
    then(chunkService).should(times(3)).processNextChunk(config);
  }
}
