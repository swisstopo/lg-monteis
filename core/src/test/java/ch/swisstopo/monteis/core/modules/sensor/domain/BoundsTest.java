package ch.swisstopo.monteis.core.modules.sensor.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import org.junit.jupiter.api.Test;

class BoundsTest {
  @Test
  void should_create_bounds_when_lower_is_less_than_upper() {
    // given
    Double lower = 10.5;
    Double upper = 50.0;

    // when
    Bounds bounds = new Bounds(lower, upper);

    // then
    assertAll(() -> assertEquals(lower, bounds.lower()), () -> assertEquals(upper, bounds.upper()));
  }

  @Test
  void should_create_bounds_when_lower_is_equal_to_upper() {
    // given
    Double lower = 25.0;
    Double upper = 25.0;

    // when
    Bounds bounds = new Bounds(lower, upper);

    // then
    assertAll(() -> assertEquals(lower, bounds.lower()), () -> assertEquals(upper, bounds.upper()));
  }

  @Test
  void should_throw_exception_when_lower_is_greater_than_upper() {
    // given
    Double lower = 100.0;
    Double upper = 10.0;

    // when
    ObjectBusinessValidationException exception =
        assertThrows(ObjectBusinessValidationException.class, () -> new Bounds(lower, upper));

    // then
    assertAll(
        () -> assertEquals("sensor.bounds.invalid", exception.getMessageKey()),
        () -> assertEquals(lower, exception.getParams().get("lower")),
        () -> assertEquals(upper, exception.getParams().get("upper")));
  }
}
