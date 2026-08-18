package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.ChunkProcessingResult;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SensorReprocessingOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(SensorReprocessingOrchestrator.class);

  private final SensorReadingRepository sensorReadingRepository;
  private final HistoricalReadingChunkProcessor chunkService;

  public SensorReprocessingOrchestrator(
      SensorReadingRepository sensorReadingRepository,
      HistoricalReadingChunkProcessor chunkService) {
    this.sensorReadingRepository = sensorReadingRepository;
    this.chunkService = chunkService;
  }

  public void checkAndReprocessHistoricalData(ActiveSensorConfig activeSensorConfig) {
    String sensorId = activeSensorConfig.getConfig().getSensorId();

    if (!sensorReadingRepository.checkOldSensorData(activeSensorConfig.getConfig())) {
      log.debug("No outdated records found for Sensor {}. Skipping reprocessing.", sensorId);
      return;
    }

    log.info(
        "Outdated records found! Initiating iterative batch reprocessing for Sensor {}", sensorId);

    int totalProcessed = 0;
    int currentBatchSize;
    OffsetDateTime cursor = null;

    do {
      ChunkProcessingResult result = chunkService.processNextChunk(activeSensorConfig, cursor);
      currentBatchSize = result.processedCount();
      cursor = result.cursor();
      totalProcessed += currentBatchSize;
    } while (currentBatchSize > 0);

    log.info(
        "Successfully reprocessed {} historical records for Sensor {}", totalProcessed, sensorId);
  }
}
