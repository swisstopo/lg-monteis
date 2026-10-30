package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.ChunkProcessingResult;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.UUID;
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
    SensorParameterConfig config = activeSensorConfig.getConfig();
    short version = config.getVersion().shortValue();
    String dasKey =
        DasKey.compose(config.getDas(), config.getDasSensorAlias(), config.getDasParameterAlias());

    reprocessingRunTimer.record(
        () -> {
          int totalProcessed =
              reprocessByDasKey(activeSensorConfig, dasKey, config.getSensorParameterId(), version);

          if (totalProcessed > 0) {
            log.info(
                "Successfully reprocessed {} historical records for DAS key {} (sensor parameter"
                    + " {})",
                totalProcessed,
                dasKey,
                config.getSensorParameterId());
          }
        });
  }

  private int reprocessByDasKey(
      ActiveSensorConfig activeSensorConfig, String dasKey, UUID sensorParameterId, short version) {
    if (!sensorReadingRepository.checkStaleSensorDataByDasKey(dasKey, sensorParameterId, version)) {
      log.debug("No outdated records found for DAS key {}. Skipping.", dasKey);
      return 0;
    }

    log.info(
        "Outdated records found! Initiating iterative batch reprocessing for DAS key {}", dasKey);

    int totalProcessed = 0;
    int currentBatchSize;
    OffsetDateTime cursor = null;
    do {
      ChunkProcessingResult result =
          chunkService.processNextChunk(activeSensorConfig, dasKey, cursor);
      currentBatchSize = result.processedCount();
      cursor = result.cursor();
      totalProcessed += currentBatchSize;
    } while (currentBatchSize > 0);

    return totalProcessed;
  }
}
