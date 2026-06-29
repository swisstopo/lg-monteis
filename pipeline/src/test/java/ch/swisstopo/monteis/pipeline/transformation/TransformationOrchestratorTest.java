package ch.swisstopo.monteis.pipeline.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.contracts.SensorConfig;
import ch.swisstopo.monteis.pipeline.jooq.generated.enums.RangeCategory;
import ch.swisstopo.monteis.pipeline.jooq.generated.tables.records.SensorReadingRecord;
import ch.swisstopo.monteis.pipeline.transformation.processing.cache.ActiveSensorConfig;
import ch.swisstopo.monteis.pipeline.transformation.standardization.SIStandardizer;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundStatus;
import ch.swisstopo.monteis.pipeline.transformation.validation.BoundsValidator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransformationOrchestratorTest {

  @Mock private SIStandardizer siStandardizer;

  @Mock private BoundsValidator boundsValidator;

  @Mock private ActiveSensorConfig activeConfig;

  @InjectMocks private TransformationOrchestrator orchestrator;

  private final SensorConfig sensorConfig = new SensorConfig("deviceA", "x", 100.0, 0.0, 1);
  private final OffsetDateTime defaultTimestamp = OffsetDateTime.now();

  @Test
  void should_successfully_parse_epoch_timestamp_and_orchestrate() {
    // given
    long epochMilli = 1718880000000L;
    String rawTimestamp = String.valueOf(epochMilli);
    Double rawValue = 15.5;
    Double standardizedValue = 20.0;

    given(activeConfig.getConfig()).willReturn(sensorConfig);
    given(siStandardizer.standardizeToSI(rawValue, activeConfig)).willReturn(standardizedValue);
    given(boundsValidator.evaluateBounds("deviceA", standardizedValue, sensorConfig))
        .willReturn(BoundStatus.OK);

    // when
    SensorReadingRecord result =
        orchestrator.transform("deviceA", rawValue, rawTimestamp, activeConfig);

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
            () -> orchestrator.transform("deviceA", 15.5, invalidTimestamp, activeConfig));

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

    given(activeConfig.getConfig()).willReturn(sensorConfig);
    given(siStandardizer.standardizeToSI(rawValue, activeConfig)).willReturn(standardizedValue);
    given(boundsValidator.evaluateBounds("deviceA", standardizedValue, sensorConfig))
        .willReturn(BoundStatus.TOO_HIGH);

    // when
    SensorReadingRecord result =
        orchestrator.transform("deviceA", rawValue, defaultTimestamp, activeConfig);

    // then
    assertThat(result.getStatus()).isEqualTo(RangeCategory.too_high);
  }

  @Test
  void should_map_too_low_status_to_correct_database_enum() {
    // given
    Double rawValue = -10.0;
    Double standardizedValue = -10.0;

    given(activeConfig.getConfig()).willReturn(sensorConfig);
    given(siStandardizer.standardizeToSI(rawValue, activeConfig)).willReturn(standardizedValue);
    given(boundsValidator.evaluateBounds("deviceA", standardizedValue, sensorConfig))
        .willReturn(BoundStatus.TOO_LOW);

    // when
    SensorReadingRecord result =
        orchestrator.transform("deviceA", rawValue, defaultTimestamp, activeConfig);

    // then
    assertThat(result.getStatus()).isEqualTo(RangeCategory.too_low);
  }

  @Test
  void should_propagate_transformation_exception_from_standardizer() {
    // given
    Double rawValue = 0.0;

    TransformationException expectedException =
        new TransformationException(
            "Math calculation failed: / by zero", new ArithmeticException("/ by zero"), rawValue);

    given(siStandardizer.standardizeToSI(rawValue, activeConfig)).willThrow(expectedException);

    // when
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () -> orchestrator.transform("deviceA", rawValue, defaultTimestamp, activeConfig));

    // then
    assertThat(exception).isSameAs(expectedException);
    assertThat(exception.getFailedPayload()).isEqualTo(rawValue);
  }
}
