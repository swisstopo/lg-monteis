package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.ChunkProcessingResult;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SensorReprocessingOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(SensorReprocessingOrchestrator.class);

  private final SensorReadingRepository sensorReadingRepository;
  private final HistoricalReadingChunkProcessor chunkService;
  private final Timer reprocessingRunTimer;

  public SensorReprocessingOrchestrator(
      SensorReadingRepository sensorReadingRepository,
      HistoricalReadingChunkProcessor chunkService,
      MeterRegistry meterRegistry) {
    this.sensorReadingRepository = sensorReadingRepository;
    this.chunkService = chunkService;
    this.reprocessingRunTimer =
        Timer.builder("pipeline.reprocessing.run.duration")
            .description(
                "Duration of a full historical-reprocessing run triggered by a sensor config"
                    + " update")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  public void checkAndReprocessHistoricalData(ActiveSensorConfig activeSensorConfig) {
    reprocessingRunTimer.record(
        () -> {
          int totalProcessed = reprocessByDasKey(activeSensorConfig);

          if (totalProcessed > 0) {
            log.info(
                "Successfully reprocessed {} historical records for DAS key {} (sensor parameter"
                    + " {})",
                totalProcessed,
                activeSensorConfig.dasKey(),
                activeSensorConfig.getConfig().getSensorParameterId());
          }
        });
  }

  private int reprocessByDasKey(ActiveSensorConfig activeSensorConfig) {
    String dasKey = activeSensorConfig.dasKey();
    if (!sensorReadingRepository.checkStaleSensorDataByDasKey(activeSensorConfig.stamp())) {
      log.debug("No outdated records found for DAS key {}. Skipping.", dasKey);
      return 0;
    }

    log.info(
        "Outdated records found! Initiating iterative batch reprocessing for DAS key {}", dasKey);

    int totalProcessed = 0;
    int currentBatchSize;
    OffsetDateTime cursor = null;
    do {
      ChunkProcessingResult result = chunkService.processNextChunk(activeSensorConfig, cursor);
      currentBatchSize = result.processedCount();
      cursor = result.cursor();
      totalProcessed += currentBatchSize;
    } while (currentBatchSize > 0);

    return totalProcessed;
  }
}
