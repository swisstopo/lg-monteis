package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.pipeline.internal.model.NormalizedSensorData;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.ProcessingOrigin;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.SensorConfigCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
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

  private final Timer processingLatencyTimer;

  private final Timer endToEndLatencyTimer;

  public SensorDataBatchProcessor(
      SensorConfigCache sensorConfigCache,
      TransformationOrchestrator orchestrator,
      SensorReadingRepository sensorReadingRepository,
      TransactionTemplate transactionTemplate,
      MeterRegistry meterRegistry) {
    this.sensorConfigCache = sensorConfigCache;
    this.orchestrator = orchestrator;
    this.sensorReadingRepository = sensorReadingRepository;
    this.transactionTemplate = transactionTemplate;
    this.processingLatencyTimer =
        Timer.builder("pipeline.message.processing.duration")
            .description(
                "Time from a normalized message becoming available on Kafka to being durably"
                    + " written to TimescaleDB")
            .publishPercentileHistogram()
            .register(meterRegistry);
    this.endToEndLatencyTimer =
        Timer.builder("pipeline.message.total.duration")
            .description(
                "Time from the original sensor-reading timestamp (source-system/device clock) to"
                    + " being durably written to TimescaleDB — includes any upstream replication"
                    + " lag (Kafka Connect/MirrorMaker2, external network) in addition to local"
                    + " processing time")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  public void processAndPersist(
      List<NormalizedSensorData> batch, List<Long> receivedTimestamps, Acknowledgment ack) {
    List<TimedRecord> timedRecords =
        IntStream.range(0, batch.size())
            .mapToObj(
                i -> {
                  NormalizedSensorData sensorData = batch.get(i);
                  try {
                    ActiveSensorConfig activeConfig =
                        sensorConfigCache.getActiveConfig(sensorData.sensorId());
                    SensorReadingRecord dbRecord =
                        orchestrator.transform(
                            sensorData.sensorId(),
                            sensorData.value(),
                            sensorData.ts(),
                            activeConfig,
                            ProcessingOrigin.INGEST);
                    return new TimedRecord(dbRecord, receivedTimestamps.get(i));
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

    List<SensorReadingRecord> dbRecords = timedRecords.stream().map(TimedRecord::dbRecord).toList();

    transactionTemplate.executeWithoutResult(
        status -> sensorReadingRepository.upsertBatch(dbRecords));

    Instant writtenAt = Instant.now();
    timedRecords.forEach(
        timedRecord -> {
          processingLatencyTimer.record(
              Duration.between(Instant.ofEpochMilli(timedRecord.receivedAtMillis()), writtenAt));
          endToEndLatencyTimer.record(
              Duration.between(timedRecord.dbRecord().getTimestamp().toInstant(), writtenAt));
        });

    ack.acknowledge();

    log.info("Successfully processed {} records.", dbRecords.size());
  }

  private record TimedRecord(SensorReadingRecord dbRecord, long receivedAtMillis) {}
}
