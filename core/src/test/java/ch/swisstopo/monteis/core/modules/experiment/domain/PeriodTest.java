package ch.swisstopo.monteis.core.modules.experiment.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.Test;

class PeriodTest {

  @Test
  void should_initialize_when_end_date_is_after_start_date() {
    // given
    LocalDate startDate = LocalDate.of(2026, Month.JANUARY, 1);
    LocalDate endDate = LocalDate.of(2026, Month.JANUARY, 10);

    // when
    Period period = new Period(startDate, endDate);

    // then
    assertAll(
        () -> assertEquals(startDate, period.start(), "Start date should be mapped correctly"),
        () -> assertEquals(endDate, period.end(), "End date should be mapped correctly"));
  }

  @Test
  void should_initialize_when_end_date_is_equal_to_start_date() {
    // given
    LocalDate sameDate = LocalDate.of(2026, Month.AUGUST, 7);

    // when
    Period period = new Period(sameDate, sameDate);

    // then
    assertAll(
        () -> assertEquals(sameDate, period.start(), "Start date should be mapped correctly"),
        () -> assertEquals(sameDate, period.end(), "End date should be mapped correctly"));
  }

  @Test
  void should_throw_exception_when_end_date_is_before_start_date() {
    // given
    LocalDate startDate = LocalDate.of(2026, Month.DECEMBER, 31);
    LocalDate endDate = LocalDate.of(2026, Month.DECEMBER, 1);

    // when & then
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> new Period(startDate, endDate),
            "Should throw validation exception when end date precedes start date");

    assertEquals(
        "experiment.period.invalid", exception.getMessage(), "Exception message code should match");
  }
}
