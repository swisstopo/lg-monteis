package ch.swisstopo.monteis.pipeline.transformation.processing.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.contracts.SensorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    "x * 2.5,               10.0,   25.0", // Simple multiplication
    "(x - 32) * (5 / 9.0),  68.0,   20.0", // Complex formula (Fahrenheit to Celsius)
    "x^2 + 10,              5.0,    35.0" // Built-in math functions (exponents)
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
    // The ExpressionParser should throw an exception immediately upon creation
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new ActiveSensorConfig(invalidConfig));

    assertThat(exception.getMessage()).isNotNull();
  }
}
