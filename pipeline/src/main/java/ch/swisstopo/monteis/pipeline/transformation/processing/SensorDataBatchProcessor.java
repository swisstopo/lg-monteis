package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.SensorConfigCache;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SensorDataBatchProcessor {

  private static final Logger log = LoggerFactory.getLogger(SensorDataBatchProcessor.class);

  private final SensorConfigCache sensorConfigCache;

  private final TransformationOrchestrator orchestrator;

  private final SensorReadingRepository sensorReadingRepository;

  private final TransactionTemplate transactionTemplate;

  public SensorDataBatchProcessor(
      SensorConfigCache sensorConfigCache,
      TransformationOrchestrator orchestrator,
      SensorReadingRepository sensorReadingRepository,
      TransactionTemplate transactionTemplate) {
    this.sensorConfigCache = sensorConfigCache;
    this.orchestrator = orchestrator;
    this.sensorReadingRepository = sensorReadingRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public void processAndPersist(List<NormalizedSensorData> batch, Acknowledgment ack) {
    List<SensorReadingRecord> dbRecords =
        batch.stream()
            .map(
                sensorData -> {
                  try {
                    ActiveSensorConfig activeConfig =
                        sensorConfigCache.getActiveConfig(sensorData.sensorId());
                    return orchestrator.transform(
                        sensorData.sensorId(), sensorData.value(), sensorData.ts(), activeConfig);
                  } catch (TransformationException ex) {
                    log.error(
                        "POISON PILL: Transformation failed for sensor {}. Failed Value: [{}]. Full"
                            + " Kafka Payload: {}. Reason: {}",
                        sensorData.sensorId(),
                        ex.getFailedPayload(),
                        sensorData,
                        ex.getMessage(),
                        ex);
                    return null;
                  }
                })
            .filter(java.util.Objects::nonNull)
            .toList();

    transactionTemplate.executeWithoutResult(
        status -> sensorReadingRepository.upsertBatch(dbRecords));

    ack.acknowledge();

    log.info("Successfully processed {} records.", dbRecords.size());
  }
}
