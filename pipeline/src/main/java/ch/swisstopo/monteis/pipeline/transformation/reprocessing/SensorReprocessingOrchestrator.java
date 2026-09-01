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
    String sensorId = activeSensorConfig.getConfig().getSensorId();

    if (!sensorReadingRepository.checkOldSensorData(activeSensorConfig.getConfig())) {
      log.debug("No outdated records found for Sensor {}. Skipping reprocessing.", sensorId);
      return;
    }

    log.info(
        "Outdated records found! Initiating iterative batch reprocessing for Sensor {}", sensorId);

    reprocessingRunTimer.record(
        () -> {
          int totalProcessed = 0;
          int currentBatchSize;
          OffsetDateTime cursor = null;

          do {
            ChunkProcessingResult result =
                chunkService.processNextChunk(activeSensorConfig, cursor);
            currentBatchSize = result.processedCount();
            cursor = result.cursor();
            totalProcessed += currentBatchSize;
          } while (currentBatchSize > 0);

          log.info(
              "Successfully reprocessed {} historical records for Sensor {}",
              totalProcessed,
              sensorId);
        });
  }
}
