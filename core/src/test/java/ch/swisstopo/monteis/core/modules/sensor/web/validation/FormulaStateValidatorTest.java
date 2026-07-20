package ch.swisstopo.monteis.core.modules.sensor.web.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FormulaStateValidatorTest {
  private FormulaStateValidator validator;
  private ConstraintValidatorContext context;

  @BeforeEach
  void setUp() {
    validator = new FormulaStateValidator();
    context = mock(ConstraintValidatorContext.class);
  }

  @Test
  void should_be_valid_when_dto_is_null() {
    assertTrue(validator.isValid(null, context));
  }

  @Test
  void should_be_valid_when_both_id_and_version_present() {
    // given
    WriteFormulaDto dto = new WriteFormulaDto(1L, "x * 2", 1);

    // when / then
    assertTrue(validator.isValid(dto, context));
  }

  @Test
  void should_be_valid_when_both_id_and_version_null() {
    // given
    WriteFormulaDto dto = new WriteFormulaDto(null, "x * 2", null);

    // when / then
    assertTrue(validator.isValid(dto, context));
  }

  @Test
  void should_be_invalid_when_id_present_but_version_null() {
    // given
    WriteFormulaDto dto = new WriteFormulaDto(1L, "x * 2", null);
    setupMockContext();

    // when / then
    assertFalse(validator.isValid(dto, context));
  }

  @Test
  void should_be_invalid_when_version_present_but_id_null() {
    // given
    WriteFormulaDto dto = new WriteFormulaDto(null, "x * 2", 1);
    setupMockContext();

    // when / then
    assertFalse(validator.isValid(dto, context));
  }

  private void setupMockContext() {
    ConstraintValidatorContext.ConstraintViolationBuilder builder =
        mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
    given(context.buildConstraintViolationWithTemplate(anyString())).willReturn(builder);
    given(builder.addConstraintViolation()).willReturn(context);
  }
}
