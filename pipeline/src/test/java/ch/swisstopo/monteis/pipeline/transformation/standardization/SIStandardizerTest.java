package ch.swisstopo.monteis.pipeline.transformation.standardization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.TransformationException;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SIStandardizerTest {

  @Mock private ActiveSensorConfig activeConfig;

  @InjectMocks private SIStandardizer siStandardizer;

  @Test
  void should_successfully_standardize_value() {
    // given
    Double rawValue = 15.5;
    Double expectedSIValue = 25.0;

    given(activeConfig.evaluate(rawValue)).willReturn(expectedSIValue);

    // when
    Double result = siStandardizer.standardizeToSI(rawValue, activeConfig);

    // then
    assertThat(result).isEqualTo(expectedSIValue);
  }

  @Test
  void should_wrap_arithmetic_exception_into_transformation_exception() {
    // given
    Double rawValue = 0.0;

    SensorConfig configMock = mock(SensorConfig.class);
    given(activeConfig.getConfig()).willReturn(configMock);
    given(configMock.getSensorId()).willReturn("device-001");
    given(configMock.getFormula()).willReturn("100 / x");

    // Simulate a divide-by-zero error during evaluation
    given(activeConfig.evaluate(rawValue)).willThrow(new ArithmeticException("/ by zero"));

    // when
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () -> siStandardizer.standardizeToSI(rawValue, activeConfig));

    // then
    assertThat(exception.getMessage()).isEqualTo("Math calculation failed: / by zero");
    assertThat(exception.getCause()).isInstanceOf(ArithmeticException.class);
    assertThat(exception.getFailedPayload()).isEqualTo(rawValue);
  }

  @Test
  void should_wrap_illegal_argument_exception_into_transformation_exception() {
    // given
    Double rawValue = -5.0;

    // Mock the underlying config used for logging when an error occurs
    SensorConfig configMock = mock(SensorConfig.class);
    given(activeConfig.getConfig()).willReturn(configMock);
    given(configMock.getSensorId()).willReturn("device-002");
    given(configMock.getFormula()).willReturn("sqrt(x)");

    // Simulate an invalid argument error (e.g., square root of a negative number)
    given(activeConfig.evaluate(rawValue))
        .willThrow(new IllegalArgumentException("Square root of negative number"));

    // when
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () -> siStandardizer.standardizeToSI(rawValue, activeConfig));

    // then
    assertThat(exception.getMessage())
        .isEqualTo("Math calculation failed: Square root of negative number");
    assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.getFailedPayload()).isEqualTo(rawValue);
  }
}
