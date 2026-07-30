package ch.swisstopo.monteis.core.modules.sensor.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SensorTypeTest {
  @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
  @CsvSource({
    // Already normalized
    "'Test Type', 'Test Type'",

    // Different capitalizations
    "'TEST Type', 'Test Type'",
    "'test type', 'Test Type'",
    "'tEsT tYpe', 'Test Type'",
    "'test TYPE', 'Test Type'",
    "'TEST TYPE', 'Test Type'",

    // Whitespace normalization
    "'   test type', 'Test Type'",
    "'test type   ', 'Test Type'",
    "'   test type   ', 'Test Type'",
    "'test    type', 'Test Type'",
    "'test\t\ttype', 'Test Type'",

    // Multiple words
    "'test type sensor', 'Test Type Sensor'",
    "'TEST TYPE SENSOR', 'Test Type Sensor'",
    "'test    TYPE    sensor', 'Test Type Sensor'",

    // Single word
    "'test', 'Test'",
    "'TEST', 'Test'",
    "'tEsT', 'Test'",

    // Mixed whitespace
    "'   TEST\t type   sensor   ', 'Test Type Sensor'"
  })
  void should_normalize_type_name(String input, String expected) {
    String normalizedName = new SensorType(null, input, null).name();
    assertEquals(expected, normalizedName);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   ", "\t", "\n", "\r\n", " \t \n "})
  void should_reject_empty_names(String input) {
    assertThatThrownBy(() -> new SensorType(null, input, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_names() {
    assertThatThrownBy(() -> new SensorType(null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
