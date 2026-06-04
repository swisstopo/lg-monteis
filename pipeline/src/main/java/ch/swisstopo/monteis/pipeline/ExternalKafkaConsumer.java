package ch.swisstopo.monteis.pipeline;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ExternalKafkaConsumer {
    Logger logger = LoggerFactory.getLogger(ExternalKafkaConsumer.class);

    @KafkaListener(topics = "solexperts_to_swisstopo")
    public void listen(ConsumerRecord<String, String> consumerRecord, Acknowledgment acknowledgment) {

        try {
            logger.info("Received message: " + consumerRecord.value());

            //acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Processing failed", e);
        }
    }
}
