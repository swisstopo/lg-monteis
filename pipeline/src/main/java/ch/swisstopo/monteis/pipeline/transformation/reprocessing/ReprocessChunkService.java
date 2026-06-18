package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.persistence.SensorReadingRepository;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.TransformationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReprocessChunkService {

    private static final Logger log = LoggerFactory.getLogger(ReprocessChunkService.class);

    private final SensorReadingRepository sensorReadingRepository;

    private final TransformationOrchestrator transformationOrchestrator;

    private final int chunkSize;

    public ReprocessChunkService(SensorReadingRepository sensorReadingRepository,
                                 TransformationOrchestrator transformationOrchestrator,
                                 @Value("${app.pipeline.reprocessing.chunk-size:1000}") int chunkSize) {
        this.sensorReadingRepository = sensorReadingRepository;
        this.transformationOrchestrator = transformationOrchestrator;
        this.chunkSize = chunkSize;
    }

    @Transactional
    public int processNextChunk(SensorConfig sensorConfig) {
        List<SensorReadingRecord> oldRecords = sensorReadingRepository.fetchOldSensorData(sensorConfig, chunkSize);

        if (oldRecords.isEmpty()) {
            return 0;
        }

        List<SensorReadingRecord> updatedRecords = oldRecords.stream()
                .map(reading -> {
                    try {
                        return transformationOrchestrator.transform(
                                reading.getSensorId(),
                                reading.getRawValue(),
                                reading.getTimestamp(), // This is already an OffsetDateTime from DB
                                sensorConfig
                        );
                    } catch (TransformationException ex) {
                        log.error("POISON PILL REPROCESSING: Math failed for historical record of sensor {}. " +
                                        "Raw Value: [{}]. Bumping version to bypass infinite loop. Reason: {}",
                                reading.getSensorId(), reading.getRawValue(), ex.getMessage(), ex);

                        // CRITICAL: We must update the version to break the do-while loop!
                        reading.setVersion(sensorConfig.getVersion().shortValue());

                        // Set norm_value to null because the new formula cannot process this specific raw value.
                        reading.setNormValue(null);

                        return reading;
                    }
                }).toList();

        sensorReadingRepository.bulkUpdate(updatedRecords);

        return updatedRecords.size();
    }
}