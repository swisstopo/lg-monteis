package ch.swisstopo.monteis.core.modules.sensor.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensorParameterTest {

  @Test
  void should_default_das_parameter_alias_to_value_when_omitted() {
    // given: mirrors the frontend omitting the alias when the DAS only reports one measurement
    SensorParameter parameter = newSensorParameterWithAlias(null);

    // when / then
    assertEquals("value", parameter.getDasParameterAlias());
  }

  @Test
  void should_default_das_parameter_alias_to_value_when_blank() {
    // given
    SensorParameter parameter = newSensorParameterWithAlias("   ");

    // when / then
    assertEquals("value", parameter.getDasParameterAlias());
  }

  @Test
  void should_keep_explicit_das_parameter_alias() {
    // given
    SensorParameter parameter = newSensorParameterWithAlias("PARAM-01");

    // when / then
    assertEquals("PARAM-01", parameter.getDasParameterAlias());
  }

  @Test
  void should_trigger_publish_when_old_parameter_is_null() {
    // given
    SensorParameter parameter = sensorParameterWith("x * 2", 0.0, 100.0);

    // when / then
    assertTrue(parameter.changeTriggersPublish(null));
  }

  @Test
  void should_trigger_publish_when_formula_expression_changed() {
    // given
    SensorParameter before = sensorParameterWith("x * 2", 0.0, 100.0);
    SensorParameter after = sensorParameterWith("x * 3", 0.0, 100.0);

    // when / then
    assertTrue(after.changeTriggersPublish(before));
  }

  @Test
  void should_not_trigger_publish_when_formula_instance_differs_but_expression_is_equal() {
    // given
    Formula beforeFormula = new Formula("x * 2");
    Formula afterFormula = new Formula("x * 2");
    SensorParameter before = sensorParameterWith(beforeFormula, new AlarmLimits(0.0, 100.0));
    SensorParameter after = sensorParameterWith(afterFormula, new AlarmLimits(0.0, 100.0));

    // when / then
    assertNotSame(
        beforeFormula, afterFormula, "Precondition: formulas must be different instances");
    assertFalse(after.changeTriggersPublish(before));
  }

  @Test
  void should_trigger_publish_when_alarm_limits_changed() {
    // given
    SensorParameter before = sensorParameterWith("x * 2", 0.0, 100.0);
    SensorParameter after = sensorParameterWith("x * 2", 0.0, 200.0);

    // when / then
    assertTrue(after.changeTriggersPublish(before));
  }

  @Test
  void should_not_trigger_publish_when_alarm_limits_and_formula_are_unchanged() {
    // given
    Formula formula = new Formula("x * 2");
    AlarmLimits alarmLimits = new AlarmLimits(0.0, 100.0);
    SensorParameter before = sensorParameterWith(formula, alarmLimits);
    SensorParameter after = sensorParameterWith(formula, alarmLimits);

    // when / then
    assertFalse(after.changeTriggersPublish(before));
  }

  @Test
  void should_not_trigger_publish_when_only_unrelated_field_changed() {
    // given
    SensorParameter before = sensorParameterWith("x * 2", 0.0, 100.0);
    before.setName("Old Name");
    SensorParameter after = sensorParameterWith("x * 2", 0.0, 100.0);
    after.setName("New Name");

    // when / then
    assertFalse(after.changeTriggersPublish(before));
  }

  private SensorParameter sensorParameterWith(
      String formulaExpression, Double lower, Double upper) {
    return sensorParameterWith(new Formula(formulaExpression), new AlarmLimits(lower, upper));
  }

  private SensorParameter sensorParameterWith(Formula formula, AlarmLimits alarmLimits) {
    return new SensorParameter(
        "Test Sensor Parameter",
        "PARAM-01",
        new SensorType(null, "Other", null),
        Unit.METER,
        formula,
        alarmLimits,
        true,
        null);
  }

  private SensorParameter newSensorParameterWithAlias(String dasParameterAlias) {
    return new SensorParameter(
        "Test Sensor Parameter",
        dasParameterAlias,
        new SensorType(null, "Other", null),
        Unit.METER,
        new Formula("x * 2"),
        new AlarmLimits(0.0, 100.0),
        true,
        null);
  }
}
