package ch.swisstopo.monteis.pipeline.ingress.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NormalizedSensorDataPublisherTest {

    @Mock
    private KafkaTemplate<String, NormalizedSensorData> kafkaTemplate;

    @Test
    void shouldDelegateToKafkaTemplate() {
        // given
        String topic = "test-topic";
        NormalizedSensorDataPublisher publisher = new NormalizedSensorDataPublisher(kafkaTemplate, topic);

        String sensorId = "deviceA";
        NormalizedSensorData payload = new NormalizedSensorData(sensorId, "2026-06-23T09:00:00Z", 15.5);
        CompletableFuture<SendResult<String, NormalizedSensorData>> expectedFuture = new CompletableFuture<>();

        given(kafkaTemplate.send(topic, sensorId, payload)).willReturn(expectedFuture);

        // when
        CompletableFuture<SendResult<String, NormalizedSensorData>> actualFuture = publisher.publish(sensorId, payload);

        // then
        then(kafkaTemplate).should().send(topic, sensorId, payload);
        assertEquals(expectedFuture, actualFuture);
    }
}