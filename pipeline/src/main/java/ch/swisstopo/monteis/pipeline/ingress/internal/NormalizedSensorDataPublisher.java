package ch.swisstopo.monteis.pipeline.ingress.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class NormalizedSensorDataPublisher {

    private final KafkaTemplate<String, NormalizedSensorData> kafkaTemplate;
    private final String normalizedTopic;

    public NormalizedSensorDataPublisher(
            KafkaTemplate<String, NormalizedSensorData> kafkaTemplate,
            @Value("${app.kafka.topics.normalized}") String normalizedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.normalizedTopic = normalizedTopic;
    }

    public CompletableFuture<SendResult<String, NormalizedSensorData>> publish(
            String sensorId, NormalizedSensorData data) {
        return kafkaTemplate.send(normalizedTopic, sensorId, data);
    }
}