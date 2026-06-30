package ch.swisstopo.monteis.pipeline.transformation.processing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.contracts.SensorConfig;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class SensorConfigMessageProcessorTest {

  @Mock private Acknowledgment ack;

  @Mock private Consumer<SensorConfig> businessLogic;

  @InjectMocks private SensorConfigMessageProcessor processor;

  @Test
  void should_acknowledge_and_skip_logic_when_payload_is_null_tombstone() {
    // when
    processor.processSafely(null, "deleted_sensor", ack, businessLogic);

    // then
    then(businessLogic).should(never()).accept(any());
    then(ack).should().acknowledge();
  }

  @Test
  void should_acknowledge_and_skip_logic_when_sensor_id_is_null() {
    // given
    SensorConfig configWithoutId = new SensorConfig(null, "x * 2", 10.0, -10.0, 1);

    // when
    processor.processSafely(configWithoutId, "some_kafka_key", ack, businessLogic);

    // then
    then(businessLogic).should(never()).accept(any());
    then(ack).should().acknowledge();
  }

  @Test
  void should_execute_business_logic_and_acknowledge_on_success() {
    // given
    SensorConfig validConfig = new SensorConfig("sensorA", "x * 2", 10.0, -10.0, 1);

    // when
    processor.processSafely(validConfig, "sensorA", ack, businessLogic);

    // then
    then(businessLogic).should().accept(validConfig);
    then(ack).should().acknowledge();
  }

  @Test
  void should_catch_illegal_argument_exception_and_still_acknowledge_poison_pill() {
    // given
    SensorConfig poisonConfig = new SensorConfig("sensorB", "invalid syntax", 10.0, -10.0, 1);

    // Simulate Parsington/Cache throwing an IllegalArgumentException for bad syntax
    willThrow(new IllegalArgumentException("Invalid math syntax"))
        .given(businessLogic)
        .accept(poisonConfig);

    // when
    processor.processSafely(poisonConfig, "sensorB", ack, businessLogic);

    // then
    // We verify the logic was attempted, but the exception was caught and ack was still called
    then(businessLogic).should().accept(poisonConfig);
    then(ack).should().acknowledge();
  }

  @Test
  void should_not_acknowledge_and_bubble_up_transient_exceptions() {
    // given
    SensorConfig validConfig = new SensorConfig("sensorC", "x * 2", 10.0, -10.0, 1);

    // Simulate a transient error like a database connection failure
    willThrow(new RuntimeException("Database timeout")).given(businessLogic).accept(validConfig);

    // when
    // The exception MUST bubble out of the processor so Kafka retries the message
    assertThrows(
        RuntimeException.class,
        () -> processor.processSafely(validConfig, "sensorC", ack, businessLogic));

    // then
    then(businessLogic).should().accept(validConfig);
    // Ensure acknowledgment is skipped!
    then(ack).should(never()).acknowledge();
  }
}
