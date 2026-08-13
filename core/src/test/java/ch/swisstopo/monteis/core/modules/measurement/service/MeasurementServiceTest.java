package ch.swisstopo.monteis.core.modules.measurement.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementServiceTest {

  private static final Long SENSOR_ID = 1L;

  @Mock private MeasurementQuery query;

  @InjectMocks private MeasurementService service;

  @Test
  void should_delegate_to_query_when_from_is_before_to() {
    // given
    OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2024-01-02T00:00:00Z");
    Optional<ChartDataResponseDto> expected = Optional.of(mock(ChartDataResponseDto.class));

    given(query.findMeasurements(SENSOR_ID, from, to)).willReturn(expected);

    // when
    Optional<ChartDataResponseDto> actual = service.findMeasurements(SENSOR_ID, from, to);

    // then
    then(query).should().findMeasurements(SENSOR_ID, from, to);
    assertEquals(expected, actual);
  }

  @Test
  void should_propagate_empty_result_when_sensor_is_absent_or_invisible() {
    // given
    OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2024-01-02T00:00:00Z");

    given(query.findMeasurements(SENSOR_ID, from, to)).willReturn(Optional.empty());

    // when / then
    assertEquals(Optional.empty(), service.findMeasurements(SENSOR_ID, from, to));
  }

  @Test
  void should_delegate_to_query_when_from_equals_to_exactly() {
    // given
    OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime to = from;

    given(query.findMeasurements(SENSOR_ID, from, to)).willReturn(Optional.empty());

    // when
    Optional<ChartDataResponseDto> actual = service.findMeasurements(SENSOR_ID, from, to);

    // then
    then(query).should().findMeasurements(SENSOR_ID, from, to);
    assertEquals(Optional.empty(), actual);
  }

  @Test
  void should_delegate_to_query_when_from_and_to_are_the_same_instant_in_different_offsets() {
    // given: same instant, expressed with different UTC offsets (10:00+02:00 == 08:00Z)
    OffsetDateTime from = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(2));
    OffsetDateTime to = OffsetDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    given(query.findMeasurements(SENSOR_ID, from, to)).willReturn(Optional.empty());

    // when
    Optional<ChartDataResponseDto> actual = service.findMeasurements(SENSOR_ID, from, to);

    // then
    then(query).should().findMeasurements(SENSOR_ID, from, to);
    assertEquals(Optional.empty(), actual);
  }

  @Test
  void should_throw_exception_when_from_is_after_to() {
    // given
    OffsetDateTime from = OffsetDateTime.parse("2024-01-02T00:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2024-01-01T00:00:00Z");

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> service.findMeasurements(SENSOR_ID, from, to));

    // then
    assertAll(
        () -> assertEquals("measurement.dateRange.invalid", exception.getMessageKey()),
        () -> assertEquals(from, exception.getParams().get("from")),
        () -> assertEquals(to, exception.getParams().get("to")));
    verifyNoInteractions(query);
  }

  @Test
  void should_throw_exception_when_from_is_after_to_by_a_single_nanosecond() {
    // given: the smallest possible violation of the from<=to invariant
    OffsetDateTime to = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime from = to.plusNanos(1);

    // when / then
    assertThrows(
        ObjectBusinessValidationException.class,
        () -> service.findMeasurements(SENSOR_ID, from, to));
    verifyNoInteractions(query);
  }
}
