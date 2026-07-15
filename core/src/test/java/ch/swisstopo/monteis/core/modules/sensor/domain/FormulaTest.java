package ch.swisstopo.monteis.core.modules.sensor.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import org.junit.jupiter.api.Test;

class FormulaTest {
  @Test
  void should_initialize_with_default_expression_when_using_no_args_constructor() {
    // given
    // (No setup required for default constructor)

    // when
    Formula formula = new Formula();

    // then
    assertAll(
        () -> assertEquals("x", formula.getExpression()),
        () -> assertNull(formula.getId()),
        () -> assertNull(formula.getVersion()));
  }

  @Test
  void should_create_new_formula_when_expression_is_valid() {
    // given
    String validExpression = "2 * x + 5";

    // when
    Formula formula = new Formula(validExpression);

    // then
    assertAll(
        () -> assertEquals(validExpression, formula.getExpression()),
        () -> assertNull(formula.getId()),
        () -> assertNull(formula.getVersion()));
  }

  @Test
  void should_rebuild_formula_when_expression_is_valid() {
    // given
    Long id = 99L;
    String validExpression = "x / 10";
    Integer version = 1;

    // when
    Formula formula = new Formula(id, validExpression, version);

    // then
    assertAll(
        () -> assertEquals(id, formula.getId()),
        () -> assertEquals(validExpression, formula.getExpression()),
        () -> assertEquals(version, formula.getVersion()));
  }

  @Test
  void should_throw_exception_when_expression_is_null() {
    // given
    String nullExpression = null;

    // when
    FieldBusinessValidationException exception =
        assertThrows(FieldBusinessValidationException.class, () -> new Formula(nullExpression));

    // then
    assertAll(
        () -> assertEquals("formulaControl", exception.getField()),
        () -> assertNull(exception.getActualValue()),
        () -> assertEquals("validation.formula.malformed", exception.getMessageKey()),
        () -> assertEquals("x", exception.getParams().get("requiredVariable")));
  }

  @Test
  void should_throw_exception_when_expression_misses_x() {
    // given
    String invalidExpression = "y * 2";

    // when
    FieldBusinessValidationException exception =
        assertThrows(FieldBusinessValidationException.class, () -> new Formula(invalidExpression));

    // then
    assertAll(
        () -> assertEquals("formulaControl", exception.getField()),
        () -> assertEquals(invalidExpression, exception.getActualValue()),
        () -> assertEquals("validation.formula.malformed", exception.getMessageKey()),
        () -> assertEquals("x", exception.getParams().get("requiredVariable")));
  }
}
