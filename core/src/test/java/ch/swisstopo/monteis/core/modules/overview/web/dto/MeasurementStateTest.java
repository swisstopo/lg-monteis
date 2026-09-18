package ch.swisstopo.monteis.core.modules.overview.web.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.swisstopo.monteis.core.modules.measurement.web.dto.MeasurementState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeasurementStateTest {

  private static final double LOWER = 10.0;
  private static final double UPPER = 90.0;

  @ParameterizedTest
  @ValueSource(doubles = {9.9, -100.0})
  void should_report_alarm_when_norm_value_below_lower_alarm_limit(double normValue) {
    assertEquals(MeasurementState.ALARM, MeasurementState.of(normValue, LOWER, UPPER, "too_low"));
  }

  @ParameterizedTest
  @ValueSource(doubles = {90.1, 1000.0})
  void should_report_alarm_when_norm_value_above_upper_alarm_limit(double normValue) {
    assertEquals(MeasurementState.ALARM, MeasurementState.of(normValue, LOWER, UPPER, "too_high"));
  }

  @Test
  void should_prefer_alarm_over_range_violation() {
    assertEquals(MeasurementState.ALARM, MeasurementState.of(5.0, LOWER, UPPER, "too_low"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"too_low", "too_high"})
  void should_report_exceeds_range_when_within_alarm_limits_but_outside_measurement_range(
      String rangeStatus) {
    assertEquals(
        MeasurementState.EXCEEDS_RANGE, MeasurementState.of(50.0, LOWER, UPPER, rangeStatus));
  }

  @ParameterizedTest
  @ValueSource(doubles = {10.0, 50.0, 90.0})
  void should_report_ok_within_both_limits_inclusive(double normValue) {
    assertEquals(MeasurementState.OK, MeasurementState.of(normValue, LOWER, UPPER, "correct"));
  }

  @Test
  void should_report_ok_when_norm_value_is_missing() {
    assertEquals(MeasurementState.OK, MeasurementState.of(null, LOWER, UPPER, "correct"));
  }

  @Test
  void should_ignore_alarm_limits_when_they_are_missing() {
    assertEquals(MeasurementState.OK, MeasurementState.of(1000.0, null, null, "correct"));
  }

  @Test
  void should_report_ok_when_range_status_is_missing() {
    assertEquals(MeasurementState.OK, MeasurementState.of(50.0, LOWER, UPPER, null));
  }
}
