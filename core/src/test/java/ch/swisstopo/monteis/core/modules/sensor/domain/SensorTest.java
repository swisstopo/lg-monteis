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
    Bounds bounds = new Bounds(0.0, 100.0);
    Formula nullFormula = null;

    // when
    Sensor sensor = new Sensor(code, name, bounds, nullFormula);

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
    Bounds bounds = new Bounds(-50.0, 50.0);
    Formula providedFormula = new Formula("x * 10");

    // when
    Sensor sensor = new Sensor(code, name, bounds, providedFormula);

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
