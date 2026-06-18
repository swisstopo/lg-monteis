package ch.swisstopo.monteis.pipeline.transformation.processing;

import ch.swisstopo.monteis.pipeline.ingress.internal.NormalizedSensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SensorDataListener {

    private static final Logger log = LoggerFactory.getLogger(SensorDataListener.class);

    private final ProcessService processService;

    public SensorDataListener(ProcessService processService) {
        this.processService = processService;
    }

    // Read in batches of 200, using 6 concurrent threads inside this single pod
    @KafkaListener(
            topics = "${app.kafka.topics.normalized}",
            groupId = "pipeline-master-group",
            concurrency = "${app.kafka.concurrency:6}"
    )
    public void consumeNormalizedSensorData(List<NormalizedSensorData> batch, Acknowledgment ack) {
        log.info("Received normalized sensor data");
        processService.processAndPersist(batch, ack);
    }
}
