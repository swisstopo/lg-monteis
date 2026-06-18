package ch.swisstopo.monteis.pipeline.transformation.reprocessing;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class SensorConfigReprocessingListener {

    private static final Logger log = LoggerFactory.getLogger(SensorConfigReprocessingListener.class);

    private final ReprocessService reprocessService;

    public SensorConfigReprocessingListener(ReprocessService reprocessService) {
        this.reprocessService = reprocessService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.sensor-config}",
            groupId = "reprocessing-group"
    )
    public void consumeSensorConfigUpdate(SensorConfig sensorConfig, Acknowledgment ack) {
        log.info("Received sensor config update for Sensor {}. Triggering reprocessing flow.", sensorConfig.getSensorId());

        // Hand off to the orchestrator service
        // If an exception occurs in the chunking process, it will bubble up and bypass the ack.
        reprocessService.checkAndReprocessHistoricalData(sensorConfig);

        // Acknowledge the message ONLY after all chunks have been safely processed
        // This guarantees zero data loss if the pod crashes mid-reprocessing.
        ack.acknowledge();
        log.debug("Successfully acknowledged sensor config update for Sensor {}", sensorConfig.getSensorId());
    }
}