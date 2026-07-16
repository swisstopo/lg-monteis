package ch.swisstopo.monteis.pipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaListenerConfig {

  /**
   * 1. Factory für Listener mit manuellem Ack (z.B. Datenbank-Speicherung, Cache-Hydrierung).
   * Hier MUSS in der Methode Acknowledgment ack injiziert und aufgerufen werden.
   */
  @Bean("manualAckFactory")
  public ConcurrentKafkaListenerContainerFactory<String, Object> manualAckFactory(
      ConsumerFactory<String, Object> consumerFactory) {

    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(false);

    // Manuelles Acknowledgment erzwungen
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    return factory;
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
