package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.ChunkProcessingResult;
import ch.swisstopo.monteis.pipeline.transformation.ProcessingOrigin;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class HistoricalReadingChunkProcessor {

  private static final Logger log = LoggerFactory.getLogger(HistoricalReadingChunkProcessor.class);

  private final SensorReadingRepository sensorReadingRepository;

  private final TransformationOrchestrator transformationOrchestrator;

  private final TransactionTemplate transactionTemplate;

  private final int chunkSize;

  private final Counter successCounter;

  private final Counter poisonPillCounter;

  public HistoricalReadingChunkProcessor(
      SensorReadingRepository sensorReadingRepository,
      TransformationOrchestrator transformationOrchestrator,
      TransactionTemplate transactionTemplate,
      @Value("${app.pipeline.reprocessing.chunk-size:1000}") int chunkSize,
      MeterRegistry meterRegistry) {
    this.sensorReadingRepository = sensorReadingRepository;
    this.transformationOrchestrator = transformationOrchestrator;
    this.transactionTemplate = transactionTemplate;
    this.chunkSize = chunkSize;
    this.successCounter =
        Counter.builder("pipeline.reprocessing.records")
            .tag("result", "success")
            .description("Historical sensor readings reprocessed, by outcome")
            .register(meterRegistry);
    this.poisonPillCounter =
        Counter.builder("pipeline.reprocessing.records")
            .tag("result", "poison_pill")
            .description("Historical sensor readings reprocessed, by outcome")
            .register(meterRegistry);
  }

  /**
   * Rows already tagged with the config's sensor_parameter_id - survives DAS-key remaps, see
   * {@code SensorReadingRepository.fetchOldSensorDataById}.
   */
  public ChunkProcessingResult processNextChunkById(
      ActiveSensorConfig activeSensorConfig, OffsetDateTime cursor) {
    SensorParameterConfig config = activeSensorConfig.getConfig();
    List<SensorReadingRecord> oldRecords =
        sensorReadingRepository.fetchOldSensorDataById(
            config.getSensorParameterId(), config.getVersion().shortValue(), chunkSize, cursor);
    return processChunk(activeSensorConfig, oldRecords, cursor);
  }

  /**
   * Rows ingested under this DAS key before sensor_parameter_id was ever known (still NULL) - the
   * one-time backfill path, see {@code SensorReadingRepository.fetchOldSensorDataByDasKeyUnbackfilled}.
   */
  public ChunkProcessingResult processNextChunkByDasKeyUnbackfilled(
      ActiveSensorConfig activeSensorConfig, String dasKey, OffsetDateTime cursor) {
    SensorParameterConfig config = activeSensorConfig.getConfig();
    List<SensorReadingRecord> oldRecords =
        sensorReadingRepository.fetchOldSensorDataByDasKeyUnbackfilled(
            dasKey, config.getVersion().shortValue(), chunkSize, cursor);
    return processChunk(activeSensorConfig, oldRecords, cursor);
  }

  private ChunkProcessingResult processChunk(
      ActiveSensorConfig activeSensorConfig,
      List<SensorReadingRecord> oldRecords,
      OffsetDateTime cursor) {
    if (oldRecords.isEmpty()) {
      return new ChunkProcessingResult(0, cursor);
    }

    List<SensorReadingRecord> updatedRecords =
        oldRecords.stream()
            .map(
                reading -> {
                  try {
                    SensorReadingRecord transformed =
                        transformationOrchestrator.transform(
                            reading.getDasKey(),
                            reading.getRawValue(),
                            reading.getTimestamp(), // This is already an OffsetDateTime from DB
                            activeSensorConfig,
                            ProcessingOrigin.REPROCESS);
                    successCounter.increment();
                    return transformed;
                  } catch (TransformationException ex) {
                    log.error(
                        "POISON PILL REPROCESSING: Math failed for historical record of sensor {}."
                            + " Raw Value: [{}]. Bumping version to bypass infinite loop. Reason:"
                            + " {}",
                        reading.getDasKey(),
                        reading.getRawValue(),
                        ex.getMessage(),
                        ex);

                    // CRITICAL: We must update the version to break the do-while loop!
                    reading.setVersion(activeSensorConfig.getConfig().getVersion().shortValue());

                    // Set norm_value to null because the new formula cannot process this specific
                    // raw value.
                    reading.setNormValue(null);

                    // Still backfill the id even on a poison pill: this row otherwise stays stuck
                    // in the unbackfilled-by-DAS-key backlog forever, since its version now equals
                    // the config's and it will never be picked up by that scan again.
                    reading.setSensorParameterId(
                        activeSensorConfig.getConfig().getSensorParameterId());

                    poisonPillCounter.increment();
                    return reading;
                  }
                })
            .toList();

    transactionTemplate.executeWithoutResult(
        status -> sensorReadingRepository.bulkUpdate(updatedRecords));

    // oldRecords is ordered by timestamp DESC, so the last entry is the oldest in this chunk —
    // the next fetch resumes strictly before it instead of re-scanning from the start.
    OffsetDateTime nextCursor = oldRecords.getLast().getTimestamp();

    return new ChunkProcessingResult(updatedRecords.size(), nextCursor);
  }
}
