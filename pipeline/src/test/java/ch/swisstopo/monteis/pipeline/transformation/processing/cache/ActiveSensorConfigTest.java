package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActiveSensorConfigTest {

  @Test
  void should_return_underlying_sensor_config() {
    // given
    SensorConfig config = new SensorConfig("deviceA", "x", 100.0, 0.0, 1);

    // when
    ActiveSensorConfig activeConfig = new ActiveSensorConfig(config);

    // then
    assertThat(activeConfig.getConfig()).isEqualTo(config);
  }

  @ParameterizedTest(name = "Given formula ''{0}'' and raw value {1}, it should evaluate to {2}")
  @CsvSource({
    "x,                     15.0,   15.0", // The identity function (single node)
    "x * 2.5,               10.0,   25.0", // Simple multiplication
    "(x - 32) * (5 / 9.0),  68.0,   20.0", // Complex formula (Fahrenheit to Celsius)
    "x^2 + 10,              5.0,    35.0", // Built-in math functions (exponents)
  })
  void should_evaluate_valid_formulas_correctly(
      String formula, Double rawValue, Double expectedResult) {
    // given
    SensorConfig config = new SensorConfig("deviceA", formula, 100.0, 0.0, 1);
    ActiveSensorConfig activeConfig = new ActiveSensorConfig(config);

    // when
    Double result = activeConfig.evaluate(rawValue);

    // then
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void should_throw_illegal_argument_exception_on_invalid_syntax_during_instantiation() {
    // given
    // A syntactically broken formula
    SensorConfig invalidConfig = new SensorConfig("deviceA", "x * / + 10", 100.0, 0.0, 1);

    // when & then
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new ActiveSensorConfig(invalidConfig));

    assertThat(exception.getMessage()).isNotNull();
  }

  @Test
  void should_throw_illegal_argument_exception_on_unknown_variable_in_expression() {
    // given
    // Valid syntax, but contains an unknown variable 'y'
    SensorConfig poisonPillConfig = new SensorConfig("deviceA", "x + y", 100.0, 0.0, 1);
    ActiveSensorConfig activeConfig = new ActiveSensorConfig(poisonPillConfig);

    // when & then
    // Parsington's internal evaluator natively throws this during operator resolution
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> activeConfig.evaluate(10.0));

    assertThat(exception.getMessage()).contains("Unknown variable: y");
  }

  @Test
  void should_throw_illegal_argument_exception_on_single_unknown_variable() {
    // given
    // A single, unknown variable with no operators to trigger native resolution
    SensorConfig poisonPillConfig = new SensorConfig("deviceA", "y", 100.0, 0.0, 1);
    ActiveSensorConfig activeConfig = new ActiveSensorConfig(poisonPillConfig);

    // when & then
    // Parsington misses this (returns the Variable object)
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> activeConfig.evaluate(10.0));

    assertThat(exception.getMessage()).contains("Unresolved variables might exist");
  }

  @ParameterizedTest(
      name = "Given formula ''{0}'', evaluating to Infinity should throw IllegalArgumentException")
  @ValueSource(
      strings = {
        "x / 0", // 10.0 / 0 evaluates to Infinity
        "x * log(0)" // 10.0 * log(0) evaluates to -Infinity
      })
  void should_throw_illegal_argument_exception_on_infinite_results(String formula) {
    // given
    SensorConfig poisonPillConfig = new SensorConfig("deviceA", formula, 100.0, 0.0, 1);
    ActiveSensorConfig activeConfig = new ActiveSensorConfig(poisonPillConfig);

    // when & then
    // The !Double.isInfinite() check in ActiveSensorConfig forces this to throw
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> activeConfig.evaluate(10.0));

    assertThat(exception.getMessage())
        .contains("Formula did not evaluate to a clean numeric value");
  }
}
