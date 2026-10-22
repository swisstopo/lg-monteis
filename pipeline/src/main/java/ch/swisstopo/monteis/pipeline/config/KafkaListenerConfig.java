package ch.swisstopo.monteis.pipeline.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaListenerConfig {

  /**
   * 1. Factory für Listener mit manuellem Ack (z.B. Datenbank-Speicherung, Cache-Hydrierung).
   * Hier MUSS in der Methode Acknowledgment ack injiziert und aufgerufen werden.
   */
  @Bean("manualAckFactory")
  public ConcurrentKafkaListenerContainerFactory<String, Object> manualAckFactory(
      ConsumerFactory<String, Object> consumerFactory,
      KafkaTemplate<String, Object> kafkaTemplate) {

    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(false);

    // Manuelles Acknowledgment erzwungen
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    factory.setCommonErrorHandler(deadLetterErrorHandler(kafkaTemplate));

    return factory;
  }

  /**
   * Retries a failing record twice with 1s backoff, then publishes it to {@code <topic>-dlt} and
   * commits it, instead of dropping it after Spring's default 10 retries. The DLT partition is left
   * to the producer, so the DLT does not need as many partitions as the source topic. If the DLT
   * publish itself fails, the record is not committed and is retried.
   */
  private static DefaultErrorHandler deadLetterErrorHandler(
      KafkaTemplate<String, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (consumerRecord, ex) -> new TopicPartition(consumerRecord.topic() + "-dlt", -1));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
  }

  /**
   * 2. Factory für deklarative @SendTo Ingress-Listener.
   * Nutzt RECORD-Ack und das Reply-Template, um Offsets erst atomar NACH dem erfolgreichen
   * Weiterleiten aller Splitter-Nachrichten zu bestätigen.
   */
  @Bean("forwardingMessageFactory")
  public ConcurrentKafkaListenerContainerFactory<String, Object> forwardingMessageFactory(
      ConsumerFactory<String, Object> consumerFactory,
      KafkaTemplate<String, Object> kafkaTemplate) {

    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(false);

    // Befähigt @SendTo zum Senden der Rückgabewerte
    factory.setReplyTemplate(kafkaTemplate);

    // Container committed den Offset automatisch, sobald die Methode fehlerfrei endet
    // UND alle @SendTo-Nachrichten erfolgreich vom Broker bestätigt wurden!
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

    return factory;
  }
}
