package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReprocessService {

  private static final Logger log = LoggerFactory.getLogger(ReprocessService.class);

  private final SensorReadingRepository sensorReadingRepository;
  private final ReprocessChunkService chunkService;

  public ReprocessService(
      SensorReadingRepository sensorReadingRepository, ReprocessChunkService chunkService) {
    this.sensorReadingRepository = sensorReadingRepository;
    this.chunkService = chunkService;
  }

  public void checkAndReprocessHistoricalData(SensorConfig sensorConfig) {
    String sensorId = sensorConfig.getSensorId();

    if (!sensorReadingRepository.checkOldSensorData(sensorConfig)) {
      log.debug("No outdated records found for Sensor {}. Skipping reprocessing.", sensorId);
      return;
    }

    log.info(
        "Outdated records found! Initiating iterative batch reprocessing for Sensor {}", sensorId);

    int totalProcessed = 0;
    int currentBatchSize;

    do {
      currentBatchSize = chunkService.processNextChunk(sensorConfig);
      totalProcessed += currentBatchSize;
    } while (currentBatchSize > 0);

    log.info(
        "Successfully reprocessed {} historical records for Sensor {}", totalProcessed, sensorId);
  }
}
