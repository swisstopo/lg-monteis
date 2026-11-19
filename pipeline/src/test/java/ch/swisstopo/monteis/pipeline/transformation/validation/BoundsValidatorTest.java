package ch.swisstopo.monteis.pipeline.transformation.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.contracts.SensorParameterConfig;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent;
import ch.swisstopo.monteis.pipeline.internal.event.SensorBoundBreachedEvent.BoundType;
import ch.swisstopo.monteis.pipeline.transformation.ProcessingOrigin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
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
    SensorParameterConfig config =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceA", "temperature", UUID.randomUUID(), "x + 1", 100.0, 0.0, 1);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceA", 50.0, config, ProcessingOrigin.INGEST);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.CORRECT);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void should_return_too_high_and_publish_upper_breach_event_with_exact_timestamp() {
    // given
    SensorParameterConfig config =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceA", "temperature", UUID.randomUUID(), "x + 1", 100.0, 0.0, 1);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceA", 150.5, config, ProcessingOrigin.INGEST);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.TOO_HIGH);

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
    SensorParameterConfig config =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceB", "temperature", UUID.randomUUID(), "x + 2", 50.0, -10.0, 1);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceB", -25.0, config, ProcessingOrigin.INGEST);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.TOO_LOW);

    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    SensorBoundBreachedEvent event = eventCaptor.getValue();

    assertThat(event.sensorId()).isEqualTo("deviceB");
    assertThat(event.standardizedValue()).isEqualTo(-25.0);
    assertThat(event.limitViolated()).isEqualTo(-10.0);
    assertThat(event.boundType()).isEqualTo(BoundType.LOWER);
    assertThat(event.timestamp()).isEqualTo(fixedTimestamp);
  }

  @Test
  void should_return_too_high_but_not_publish_event_when_reprocessing() {
    // given
    SensorParameterConfig config =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceA", "temperature", UUID.randomUUID(), "x + 1", 100.0, 0.0, 1);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceA", 150.5, config, ProcessingOrigin.REPROCESS);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.TOO_HIGH);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void should_return_too_low_but_not_publish_event_when_reprocessing() {
    // given
    SensorParameterConfig config =
        new SensorParameterConfig(
            Das.SOL_EXPERTS, "deviceB", "temperature", UUID.randomUUID(), "x + 2", 50.0, -10.0, 1);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceB", -25.0, config, ProcessingOrigin.REPROCESS);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.TOO_LOW);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void should_mark_too_high_without_alert_when_notifications_are_off() {
    // given
    SensorParameterConfig config = alarmConfig(100.0, 0.0).notificationActive(false);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceA", 150.0, config, ProcessingOrigin.INGEST);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.TOO_HIGH);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void should_alert_on_special_alarm_limits_without_changing_status_when_closely_monitored() {
    // given: inside the alarm limits, above the special alarm limits
    SensorParameterConfig config =
        alarmConfig(100.0, 0.0)
            .closelyMonitored(true)
            .upperSpecialAlarmLimit(80.0)
            .lowerSpecialAlarmLimit(10.0);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceA", 90.0, config, ProcessingOrigin.INGEST);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.CORRECT);
    then(eventPublisher).should().publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().limitViolated()).isEqualTo(80.0);
  }

  @Test
  void should_alert_twice_when_alarm_and_special_alarm_limits_are_breached() {
    // given
    SensorParameterConfig config =
        alarmConfig(100.0, 0.0)
            .closelyMonitored(true)
            .upperSpecialAlarmLimit(80.0)
            .lowerSpecialAlarmLimit(10.0);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceA", 150.0, config, ProcessingOrigin.INGEST);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.TOO_HIGH);
    then(eventPublisher).should(times(2)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .extracting(SensorBoundBreachedEvent::limitViolated)
        .containsExactly(100.0, 80.0);
  }

  @Test
  void should_ignore_special_alarm_limits_when_not_closely_monitored() {
    // given
    SensorParameterConfig config =
        alarmConfig(100.0, 0.0).upperSpecialAlarmLimit(80.0).lowerSpecialAlarmLimit(10.0);

    // when
    MeasurementStatus status =
        boundsValidator.evaluateBounds("deviceA", 90.0, config, ProcessingOrigin.INGEST);

    // then
    assertThat(status).isEqualTo(MeasurementStatus.CORRECT);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  private static SensorParameterConfig alarmConfig(Double upper, Double lower) {
    return new SensorParameterConfig(
        Das.SOL_EXPERTS, "deviceA", "temperature", UUID.randomUUID(), "x", upper, lower, 1);
  }
}
