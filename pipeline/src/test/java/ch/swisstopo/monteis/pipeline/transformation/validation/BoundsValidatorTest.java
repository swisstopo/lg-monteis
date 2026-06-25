package ch.swisstopo.monteis.pipeline.transformation.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.events.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.transformation.events.SensorBoundBreachedEvent.BoundType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BoundsValidatorTest {

  @Mock private ApplicationEventPublisher eventPublisher;

  @Captor private ArgumentCaptor<SensorBoundBreachedEvent> eventCaptor;

  private BoundsValidator boundsValidator;

  private final Instant fixedTimestamp = Instant.parse("2026-06-23T15:30:00Z");

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(fixedTimestamp, ZoneId.of("UTC"));
    boundsValidator = new BoundsValidator(eventPublisher, fixedClock);
  }

  @Test
  void should_return_ok_when_value_is_strictly_within_bounds() {
    // given
    SensorConfig config = new SensorConfig("deviceA", Map.of(), 100.0, 0.0, 1);

    // when
    BoundStatus status = boundsValidator.evaluateBounds("deviceA", 50.0, config);

    // then
    assertThat(status).isEqualTo(BoundStatus.OK);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void should_return_too_high_and_publish_upper_breach_event_with_exact_timestamp() {
    // given
    SensorConfig config = new SensorConfig("deviceA", Map.of(), 100.0, 0.0, 1);

    // when
    BoundStatus status = boundsValidator.evaluateBounds("deviceA", 150.5, config);

    // then
    assertThat(status).isEqualTo(BoundStatus.TOO_HIGH);

    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    SensorBoundBreachedEvent event = eventCaptor.getValue();

    assertThat(event.sensorId()).isEqualTo("deviceA");
    assertThat(event.standardizedValue()).isEqualTo(150.5);
    assertThat(event.limitViolated()).isEqualTo(100.0);
    assertThat(event.boundType()).isEqualTo(BoundType.UPPER);
    assertThat(event.timestamp()).isEqualTo(fixedTimestamp);
  }

  @Test
  void should_return_too_low_and_publish_lower_breach_event_with_exact_timestamp() {
    // given
    SensorConfig config = new SensorConfig("deviceB", Map.of(), 50.0, -10.0, 1);

    // when
    BoundStatus status = boundsValidator.evaluateBounds("deviceB", -25.0, config);

    // then
    assertThat(status).isEqualTo(BoundStatus.TOO_LOW);

    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    SensorBoundBreachedEvent event = eventCaptor.getValue();

    assertThat(event.sensorId()).isEqualTo("deviceB");
    assertThat(event.standardizedValue()).isEqualTo(-25.0);
    assertThat(event.limitViolated()).isEqualTo(-10.0);
    assertThat(event.boundType()).isEqualTo(BoundType.LOWER);
    assertThat(event.timestamp()).isEqualTo(fixedTimestamp);
  }
}
