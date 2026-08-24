package ch.swisstopo.monteis.core.modules.sensor.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void should_trigger_publish_when_old_sensor_is_null() {
    // given
    Sensor sensor = sensorWith("x * 2", 0.0, 100.0);

    // then
    assertTrue(sensor.changeTriggersPublish(null));
  }

  @Test
  void should_trigger_publish_when_formula_expression_changed() {
    // given
    Sensor before = sensorWith("x * 2", 0.0, 100.0);
    Sensor after = sensorWith("x * 3", 0.0, 100.0);

    // then
    assertTrue(after.changeTriggersPublish(before));
  }

  @Test
  void should_not_trigger_publish_when_formula_instance_differs_but_expression_is_equal() {
    // given
    Formula beforeFormula = new Formula("x * 2");
    Formula afterFormula = new Formula("x * 2");
    Sensor before = sensorWith(beforeFormula, new AlarmLimits(0.0, 100.0));
    Sensor after = sensorWith(afterFormula, new AlarmLimits(0.0, 100.0));

    // then
    assertAll(
        () ->
            assertNotSame(
                beforeFormula, afterFormula, "Precondition: formulas must be different instances"),
        () -> assertFalse(after.changeTriggersPublish(before)));
  }

  @Test
  void should_trigger_publish_when_alarm_limits_changed() {
    // given
    Sensor before = sensorWith("x * 2", 0.0, 100.0);
    Sensor after = sensorWith("x * 2", 0.0, 200.0);

    // then
    assertTrue(after.changeTriggersPublish(before));
  }

  @Test
  void should_not_trigger_publish_when_alarm_limits_and_formula_are_unchanged() {
    // given
    Formula formula = new Formula("x * 2");
    AlarmLimits alarmLimits = new AlarmLimits(0.0, 100.0);
    Sensor before = sensorWith(formula, alarmLimits);
    Sensor after = sensorWith(formula, alarmLimits);

    // then
    assertFalse(after.changeTriggersPublish(before));
  }

  @Test
  void should_not_trigger_publish_when_only_unrelated_field_changed() {
    // given
    Sensor before = sensorWith("x * 2", 0.0, 100.0);
    before.setName("Old Name");
    Sensor after = sensorWith("x * 2", 0.0, 100.0);
    after.setName("New Name");

    // then
    assertFalse(after.changeTriggersPublish(before));
  }

  private Sensor sensorWith(String formulaExpression, Double lower, Double upper) {
    return sensorWith(new Formula(formulaExpression), new AlarmLimits(lower, upper));
  }

  private Sensor sensorWith(Formula formula, AlarmLimits alarmLimits) {
    return new Sensor(
        "SENS-01",
        "Test Sensor",
        new SensorType(null, "Other", null),
        Unit.METER,
        null,
        new Coordinates(0, 0, 0),
        alarmLimits,
        true,
        formula);
  }
}
