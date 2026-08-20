package ch.swisstopo.monteis.core.infrastructure.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NullOrNotBlankValidator implements ConstraintValidator<NullOrNotBlank, String> {

  @Override
  public void initialize(NullOrNotBlank parameters) {
    // Empty Constructor
  }

  public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
    return value == null || !value.trim().isEmpty();
  }
}
