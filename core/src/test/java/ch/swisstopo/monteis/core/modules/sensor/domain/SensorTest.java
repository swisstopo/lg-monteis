package ch.swisstopo.monteis.core.modules.sensor.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SensorTest {
  @Test
  void should_initialize_with_default_formula_when_null_is_provided() {
    // given
    String code = "SENS-01";
    String name = "Test Sensor";
    AlarmLimits alarmLimits = new AlarmLimits(0.0, 100.0);
    Formula nullFormula = null;

    // when
    Sensor sensor =
        new Sensor(
            code,
            name,
            new SensorType(null, "Other", null),
            Unit.METER,
            null,
            new Coordinates(2400, -12007, -1600),
            alarmLimits,
            true,
            nullFormula);

    // then
    assertAll(
        () -> assertNotNull(sensor.getFormula(), "Formula should not be null"),
        () ->
            assertEquals(
                "x", sensor.getFormula().getExpression(), "Formula should default to 'x'"));
  }

  @Test
  void should_retain_provided_formula_when_not_null() {
    // given
    String code = "SENS-02";
    String name = "Custom Sensor";
    AlarmLimits alarmLimits = new AlarmLimits(-50.0, 50.0);
    Formula providedFormula = new Formula("x * 10");

    // when
    Sensor sensor =
        new Sensor(
            code,
            name,
            new SensorType(null, "Other", null),
            Unit.METER,
            null,
            new Coordinates(2400, -12007, -1600),
            alarmLimits,
            true,
            providedFormula);

    // then
    assertAll(
        () -> assertNotNull(sensor.getFormula()),
        () ->
            assertEquals(
                "x * 10",
                sensor.getFormula().getExpression(),
                "Should use the provided formula expression"));
  }
}
