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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementServiceTest {

  @Mock private MeasurementQuery query;

  @InjectMocks private MeasurementService service;

  @Test
  void should_delegate_to_query_when_from_is_before_to() {
    // given
    List<Long> ids = List.of(1L, 2L);
    OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2024-01-02T00:00:00Z");
    ChartDataResponseDto mockDto = mock(ChartDataResponseDto.class);
    List<ChartDataResponseDto> expectedResults = List.of(mockDto);

    given(query.findMeasurements(ids, from, to)).willReturn(expectedResults);

    // when
    List<ChartDataResponseDto> actualResults = service.findMeasurements(ids, from, to);

    // then
    then(query).should().findMeasurements(ids, from, to);
    assertEquals(expectedResults, actualResults);
  }

  @Test
  void should_delegate_to_query_when_from_equals_to_exactly() {
    // given
    List<Long> ids = List.of(1L);
    OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime to = from;

    given(query.findMeasurements(ids, from, to)).willReturn(List.of());

    // when
    List<ChartDataResponseDto> actualResults = service.findMeasurements(ids, from, to);

    // then
    then(query).should().findMeasurements(ids, from, to);
    assertEquals(List.of(), actualResults);
  }

  @Test
  void should_delegate_to_query_when_from_and_to_are_the_same_instant_in_different_offsets() {
    // given: same instant, expressed with different UTC offsets (10:00+02:00 == 08:00Z)
    List<Long> ids = List.of(1L);
    OffsetDateTime from = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(2));
    OffsetDateTime to = OffsetDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    given(query.findMeasurements(ids, from, to)).willReturn(List.of());

    // when
    List<ChartDataResponseDto> actualResults = service.findMeasurements(ids, from, to);

    // then
    then(query).should().findMeasurements(ids, from, to);
    assertEquals(List.of(), actualResults);
  }

  @Test
  void should_throw_exception_when_from_is_after_to() {
    // given
    List<Long> ids = List.of(1L);
    OffsetDateTime from = OffsetDateTime.parse("2024-01-02T00:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2024-01-01T00:00:00Z");

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class, () -> service.findMeasurements(ids, from, to));

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
    List<Long> ids = List.of(1L);
    OffsetDateTime to = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    OffsetDateTime from = to.plusNanos(1);

    // when / then
    assertThrows(
        ObjectBusinessValidationException.class, () -> service.findMeasurements(ids, from, to));
    verifyNoInteractions(query);
  }
}
