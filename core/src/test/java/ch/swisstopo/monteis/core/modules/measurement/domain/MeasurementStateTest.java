package ch.swisstopo.monteis.core.modules.measurement.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MeasurementStatusTest {

  @ParameterizedTest
  @CsvSource({"TOO_LOW, too_low", "CORRECT, correct", "TOO_HIGH, too_high"})
  void getDbValue_returnsCorrectString(MeasurementStatus state, String expectedDbValue) {
    assertEquals(expectedDbValue, state.getDbValue());
  }

  @ParameterizedTest
  @CsvSource({"too_low, TOO_LOW", "correct, CORRECT", "too_high, TOO_HIGH"})
  void fromDbValue_returnsCorrectEnum(String dbValue, MeasurementStatus expectedState) {
    assertEquals(expectedState, MeasurementStatus.fromDbValue(dbValue));
  }

  @Test
  void fromDbValue_withNull_returnsNull() {
    assertNull(MeasurementStatus.fromDbValue(null));
  }

  @Test
  void fromDbValue_withUnknownValue_throwsIllegalArgumentException() {
    String invalidValue = "unknown_state";

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> MeasurementStatus.fromDbValue(invalidValue));

    assertEquals("Unknown MeasurementStatus db value: " + invalidValue, exception.getMessage());
  }
}
