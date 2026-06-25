package ch.swisstopo.monteis.pipeline.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.transformation.standardization.SIStandardizer;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundStatus;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundsValidator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransformationOrchestratorTest {

  @Mock private SIStandardizer siStandardizer;

  @Mock private BoundsValidator boundsValidator;

  @InjectMocks private TransformationOrchestrator orchestrator;

  private final SensorConfig defaultConfig = new SensorConfig("deviceA", Map.of(), 100.0, 0.0, 1);
  private final OffsetDateTime defaultTimestamp = OffsetDateTime.now();

  @Test
  void should_successfully_parse_epoch_timestamp_and_orchestrate() {
    // given
    long epochMilli = 1718880000000L;
    String rawTimestamp = String.valueOf(epochMilli);
    Double rawValue = 15.5;
    Double standardizedValue = 20.0;

    given(siStandardizer.standardizeToSI(rawValue, defaultConfig)).willReturn(standardizedValue);
    given(boundsValidator.evaluateBounds("deviceA", standardizedValue, defaultConfig))
        .willReturn(BoundStatus.OK);

    // when
    SensorReadingRecord result =
        orchestrator.transform("deviceA", rawValue, rawTimestamp, defaultConfig);

    // then
    assertThat(result.getSensorId()).isEqualTo("deviceA");
    assertThat(result.getRawValue()).isEqualTo(15.5);
    assertThat(result.getNormValue()).isEqualTo(20.0);
    assertThat(result.getStatus()).isEqualTo(RangeCategory.correct);
    assertThat(result.getVersion()).isEqualTo((short) 1);

    // Verify the timestamp was parsed correctly
    OffsetDateTime expectedTimestamp =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
    assertThat(result.getTimestamp()).isEqualTo(expectedTimestamp);
  }

  @Test
  void should_throw_transformation_exception_when_timestamp_format_is_invalid() {
    // given
    String invalidTimestamp = "not-a-number";

    // when
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () -> orchestrator.transform("deviceA", 15.5, invalidTimestamp, defaultConfig));

    // then
    assertThat(exception.getMessage()).contains("Invalid epoch timestamp format: 'not-a-number'");
    assertThat(exception.getCause()).isInstanceOf(NumberFormatException.class);

    // Standardizer should never be reached
    then(siStandardizer).shouldHaveNoInteractions();
  }

  @Test
  void should_map_too_high_status_to_correct_database_enum() {
    // given
    Double rawValue = 150.0;
    Double standardizedValue = 150.0;

    given(siStandardizer.standardizeToSI(rawValue, defaultConfig)).willReturn(standardizedValue);
    given(boundsValidator.evaluateBounds("deviceA", standardizedValue, defaultConfig))
        .willReturn(BoundStatus.TOO_HIGH);

    // when
    SensorReadingRecord result =
        orchestrator.transform("deviceA", rawValue, defaultTimestamp, defaultConfig);

    // then
    assertThat(result.getStatus()).isEqualTo(RangeCategory.too_high);
  }

  @Test
  void should_map_too_low_status_to_correct_database_enum() {
    // given
    Double rawValue = -10.0;
    Double standardizedValue = -10.0;

    given(siStandardizer.standardizeToSI(rawValue, defaultConfig)).willReturn(standardizedValue);
    given(boundsValidator.evaluateBounds("deviceA", standardizedValue, defaultConfig))
        .willReturn(BoundStatus.TOO_LOW);

    // when
    SensorReadingRecord result =
        orchestrator.transform("deviceA", rawValue, defaultTimestamp, defaultConfig);

    // then
    assertThat(result.getStatus()).isEqualTo(RangeCategory.too_low);
  }

  @Test
  void should_wrap_math_exceptions_into_transformation_exception() {
    // given
    Double rawValue = 0.0;

    // Simulate a division by zero or similar math error in the standardizer
    given(siStandardizer.standardizeToSI(rawValue, defaultConfig))
        .willThrow(new ArithmeticException("/ by zero"));

    // when
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () -> orchestrator.transform("deviceA", rawValue, defaultTimestamp, defaultConfig));

    // then
    assertThat(exception.getMessage()).isEqualTo("Failed to calculate normalized value");
    assertThat(exception.getCause()).isInstanceOf(ArithmeticException.class);

    // Assert that the raw payload was safely captured in the exception for logging
    // (Assuming your TransformationException constructor accepts the rawValue and exposes it via
    // getFailedPayload())
    assertThat(exception.getFailedPayload()).isEqualTo(rawValue);
  }

  @Test
  void should_wrap_null_pointer_exceptions_into_transformation_exception() {
    // given
    Double nullRawValue = null;

    // Simulate a null pointer exception if the formula evaluator chokes on a null value
    given(siStandardizer.standardizeToSI(nullRawValue, defaultConfig))
        .willThrow(new NullPointerException("Value cannot be null"));

    // when
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () -> orchestrator.transform("deviceA", nullRawValue, defaultTimestamp, defaultConfig));

    // then
    assertThat(exception.getMessage()).isEqualTo("Failed to calculate normalized value");
    assertThat(exception.getCause()).isInstanceOf(NullPointerException.class);
    assertThat(exception.getFailedPayload()).isNull();
  }
}
