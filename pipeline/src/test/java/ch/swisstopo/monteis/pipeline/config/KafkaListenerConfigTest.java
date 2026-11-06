package ch.swisstopo.monteis.pipeline.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.sql.SQLTransientConnectionException;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

@ExtendWith(MockitoExtension.class)
class KafkaListenerConfigTest {

  private static final String TOPIC = "internal-normalized";

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;
  @Mock private Consumer<String, Object> consumer;
  @Mock private MessageListenerContainer container;
  @Captor private ArgumentCaptor<ProducerRecord<String, Object>> dltRecord;

  private final ConsumerRecord<String, Object> record =
      new ConsumerRecord<>(TOPIC, 3, 42L, "key", "value");

  private DefaultErrorHandler errorHandler;

  @BeforeEach
  void setUp() {
    ConsumerGroupMetadata groupMetadata = mock(ConsumerGroupMetadata.class);
    lenient().when(groupMetadata.groupId()).thenReturn("normalization-processor-group");

    lenient().when(consumer.groupMetadata()).thenReturn(groupMetadata);

    lenient().when(container.isRunning()).thenReturn(true);
    errorHandler = KafkaListenerConfig.deadLetterErrorHandler(kafkaTemplate);
  }

  @Test
  void should_retry_twice_then_publish_to_the_dlt_of_the_source_topic() {
    // given
    given(kafkaTemplate.send(dltRecord.capture()))
        .willReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
    Exception failure = listenerFailure(new IllegalStateException("cannot process"));

    // when / then
    assertThat(handle(failure)).isFalse();
    assertThat(handle(failure)).isFalse();
    then(kafkaTemplate).should(never()).send(any(ProducerRecord.class));

    assertThat(handle(failure)).isTrue();
    assertThat(dltRecord.getValue().topic()).isEqualTo(TOPIC + "-dlt");
    assertThat(dltRecord.getValue().partition()).isNull();
    assertThat(dltRecord.getValue().key()).isEqualTo("key");
  }

  @Test
  void should_not_commit_the_record_when_the_dlt_publish_fails() {
    // given
    CompletableFuture<SendResult<String, Object>> failedPublish = new CompletableFuture<>();
    failedPublish.completeExceptionally(new KafkaException("DLT not available"));
    given(kafkaTemplate.send(any(ProducerRecord.class)))
        .willReturn(failedPublish)
        .willReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
    Exception failure = listenerFailure(new IllegalStateException("cannot process"));
    handle(failure);
    handle(failure);

    // when / then
    assertThat(handle(failure)).isFalse();
    assertThat(handle(failure)).isFalse();
    assertThat(handle(failure)).isFalse();
    assertThat(handle(failure)).isTrue();
    then(kafkaTemplate).should(times(2)).send(any(ProducerRecord.class));
  }

  @Test
  void should_pause_until_the_database_is_back_instead_of_using_the_dlt() {
    // given
    for (Exception databaseUnavailable :
        new Exception[] {
          new QueryTimeoutException("statement timeout"),
          new CannotAcquireLockException("deadlock"),
          new CannotGetJdbcConnectionException("no connection"),
          new CannotCreateTransactionException(
              "Could not open JDBC Connection for transaction",
              new SQLTransientConnectionException("HikariPool-1 - Connection is not available")),
        }) {
      // when
      BackOff backOff = KafkaListenerConfig.backOffFor(listenerFailure(databaseUnavailable));

      // then
      assertThat(backOff).as(databaseUnavailable.toString()).isNotNull();
      BackOffExecution execution = backOff.start();
      long interval = 0;
      for (int attempt = 0; attempt < 1_000; attempt++) {
        interval = execution.nextBackOff();
        assertThat(interval).isNotEqualTo(BackOffExecution.STOP);
      }
      assertThat(interval).isEqualTo(KafkaListenerConfig.MAX_DATABASE_BACKOFF_MS);
    }
  }

  @Test
  void should_use_the_default_backoff_for_failures_unrelated_to_the_database() {
    assertThat(KafkaListenerConfig.backOffFor(listenerFailure(new IllegalStateException("bad"))))
        .isNull();
    assertThat(
            KafkaListenerConfig.backOffFor(
                listenerFailure(new DataIntegrityViolationException("duplicate key"))))
        .isNull();
  }

  private boolean handle(Exception failure) {
    return errorHandler.handleOne(failure, record, consumer, container);
  }

  private static Exception listenerFailure(Throwable cause) {
    return new ListenerExecutionFailedException("Listener failed", cause);
  }
}
