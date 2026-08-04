package ch.swisstopo.monteis.core.modules.sensor.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import org.junit.jupiter.api.Test;

class AlarmLimitsTest {
  @Test
  void should_create_alarmLimits_when_lower_is_less_than_upper() {
    // given
    Double lower = 10.5;
    Double upper = 50.0;

    // when
    AlarmLimits alarmLimits = new AlarmLimits(lower, upper);

    // then
    assertAll(
        () -> assertEquals(lower, alarmLimits.lower()),
        () -> assertEquals(upper, alarmLimits.upper()));
  }

  @Test
  void should_create_alarmLimits_when_lower_is_equal_to_upper() {
    // given
    Double lower = 25.0;
    Double upper = 25.0;

    // when
    AlarmLimits alarmLimits = new AlarmLimits(lower, upper);

    // then
    assertAll(
        () -> assertEquals(lower, alarmLimits.lower()),
        () -> assertEquals(upper, alarmLimits.upper()));
  }

  @Test
  void should_throw_exception_when_lower_is_greater_than_upper() {
    // given
    Double lower = 100.0;
    Double upper = 10.0;

    // when
    ObjectBusinessValidationException exception =
        assertThrows(ObjectBusinessValidationException.class, () -> new AlarmLimits(lower, upper));

    // then
    assertAll(
        () -> assertEquals("sensor.alarmLimits.invalid", exception.getMessageKey()),
        () -> assertEquals(lower, exception.getParams().get("lower")),
        () -> assertEquals(upper, exception.getParams().get("upper")));
  }
}
