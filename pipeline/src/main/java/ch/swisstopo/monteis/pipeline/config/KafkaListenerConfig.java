package ch.swisstopo.monteis.pipeline.config;

import java.sql.SQLTransientException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaListenerConfig {

  /**
   * 1. Factory für Listener mit manuellem Ack (z.B. Datenbank-Speicherung, Cache-Hydrierung).
   * Hier MUSS in der Methode Acknowledgment ack injiziert und aufgerufen werden.
   */
  @Bean("manualAckFactory")
  public ConcurrentKafkaListenerContainerFactory<String, Object> manualAckFactory(
      ConsumerFactory<String, Object> consumerFactory) {
    return manualAckFactoryWith(consumerFactory);
  }

  // dlt only for sensor data: cache hydration's group per pod would write one dlt copy per pod
  @Bean("sensorDataFactory")
  public ConcurrentKafkaListenerContainerFactory<String, Object> sensorDataFactory(
      ConsumerFactory<String, Object> consumerFactory,
      KafkaTemplate<String, Object> kafkaTemplate) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        manualAckFactoryWith(consumerFactory);
    factory.setCommonErrorHandler(deadLetterErrorHandler(kafkaTemplate));
    return factory;
  }

  private static ConcurrentKafkaListenerContainerFactory<String, Object> manualAckFactoryWith(
      ConsumerFactory<String, Object> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(false);

    // Manuelles Acknowledgment erzwungen
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    return factory;
  }

  static final long MAX_DATABASE_BACKOFF_MS = 30_000L;

  /**
   * Retries a failing record twice with 1s backoff, then publishes it to {@code <topic>-dlt} and
   * commits it, instead of dropping it after Spring's default 10 retries. The DLT partition is left
   * to the producer, so the DLT does not need as many partitions as the source topic. If the DLT
   * publish itself fails, the record is not committed and is retried. While the database is
   * unavailable, the record is retried with an exponential backoff (max 30s) until the database
   * answers again, so the partition pauses instead of filling the DLT.
   */
  static DefaultErrorHandler deadLetterErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (consumerRecord, ex) -> new TopicPartition(consumerRecord.topic() + "-dlt", -1));
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    errorHandler.setBackOffFunction((consumerRecord, ex) -> backOffFor(ex));
    return errorHandler;
  }

  static BackOff backOffFor(Exception ex) {
    return isDatabaseUnavailable(ex) ? untilDatabaseIsBack() : null;
  }

  private static boolean isDatabaseUnavailable(Exception ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof TransientDataAccessException
          || cause instanceof RecoverableDataAccessException
          || cause instanceof DataAccessResourceFailureException
          || cause instanceof CannotCreateTransactionException
          || cause instanceof SQLTransientException) {
        return true;
      }
    }
    return false;
  }

  private static BackOff untilDatabaseIsBack() {
    ExponentialBackOff backOff = new ExponentialBackOff();
    backOff.setInitialInterval(1_000L);
    backOff.setMultiplier(2.0);
    backOff.setMaxInterval(MAX_DATABASE_BACKOFF_MS);
    return backOff;
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
