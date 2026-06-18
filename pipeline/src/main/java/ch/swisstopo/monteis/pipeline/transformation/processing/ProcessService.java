package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorData;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.SensorConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProcessService {

    private static final Logger log = LoggerFactory.getLogger(ProcessService.class);

    private final SensorConfigCache sensorConfigCache;

    private final TransformationOrchestrator orchestrator;

    private final SensorReadingRepository sensorReadingRepository;

    public ProcessService(SensorConfigCache sensorConfigCache, TransformationOrchestrator orchestrator, SensorReadingRepository sensorReadingRepository) {
        this.sensorConfigCache = sensorConfigCache;
        this.orchestrator = orchestrator;
        this.sensorReadingRepository = sensorReadingRepository;
    }

    @Transactional
    public void processAndPersist(List<NormalizedSensorData> batch, Acknowledgment ack) {
        List<SensorReadingRecord> dbRecords = batch.stream().map(sensorData -> {
            try {
                SensorConfig config = sensorConfigCache.getSensorConfig(sensorData.sensorId());
                return orchestrator.transform(sensorData.sensorId(), sensorData.value(), sensorData.ts(), config);
            } catch (TransformationException ex) {
                log.error("POISON PILL: Transformation failed for sensor {}. Failed Value: [{}]. Full Kafka Payload: {}. Reason: {}",
                        sensorData.sensorId(),
                        ex.getFailedPayload(),
                        sensorData,
                        ex.getMessage(),
                        ex);
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();

        sensorReadingRepository.upsertBatch(dbRecords);

        ack.acknowledge();

        log.info("Successfully processed {} records.", dbRecords.size());
    }
}