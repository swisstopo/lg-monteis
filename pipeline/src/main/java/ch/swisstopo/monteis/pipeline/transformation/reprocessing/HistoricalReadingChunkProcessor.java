package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
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

  public HistoricalReadingChunkProcessor(
      SensorReadingRepository sensorReadingRepository,
      TransformationOrchestrator transformationOrchestrator,
      TransactionTemplate transactionTemplate,
      @Value("${app.pipeline.reprocessing.chunk-size:1000}") int chunkSize) {
    this.sensorReadingRepository = sensorReadingRepository;
    this.transformationOrchestrator = transformationOrchestrator;
    this.transactionTemplate = transactionTemplate;
    this.chunkSize = chunkSize;
  }

  public int processNextChunk(ActiveSensorConfig activeSensorConfig) {
    List<SensorReadingRecord> oldRecords =
        sensorReadingRepository.fetchOldSensorData(activeSensorConfig.getConfig(), chunkSize);

    if (oldRecords.isEmpty()) {
      return 0;
    }

    List<SensorReadingRecord> updatedRecords =
        oldRecords.stream()
            .map(
                reading -> {
                  try {
                    return transformationOrchestrator.transform(
                        reading.getSensorId(),
                        reading.getRawValue(),
                        reading.getTimestamp(), // This is already an OffsetDateTime from DB
                        activeSensorConfig);
                  } catch (TransformationException ex) {
                    log.error(
                        "POISON PILL REPROCESSING: Math failed for historical record of sensor {}."
                            + " Raw Value: [{}]. Bumping version to bypass infinite loop. Reason:"
                            + " {}",
                        reading.getSensorId(),
                        reading.getRawValue(),
                        ex.getMessage(),
                        ex);

                    // CRITICAL: We must update the version to break the do-while loop!
                    reading.setVersion(activeSensorConfig.getConfig().getVersion().shortValue());

                    // Set norm_value to null because the new formula cannot process this specific
                    // raw value.
                    reading.setNormValue(null);

                    return reading;
                  }
                })
            .toList();

    transactionTemplate.executeWithoutResult(
        status -> sensorReadingRepository.bulkUpdate(updatedRecords));

    return updatedRecords.size();
  }
}
