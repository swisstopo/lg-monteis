package ch.swisstopo.monteis.core.modules.sensor.web.validation;

import ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound.WriteFormulaDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator backing the {@link ValidFormulaState} constraint.
 *
 * <p>The validator ensures that a formula DTO represents either a new formula
 * or an existing formula:
 *
 * <ul>
 *   <li><strong>Existing formula:</strong> {@code id} and {@code version} are
 *       both present.</li>
 *   <li><strong>New formula:</strong> {@code id} and {@code version} are both
 *       absent.</li>
 * </ul>
 *
 * <p>A DTO containing only one of these values represents an inconsistent
 * state and is rejected.
 */
public class FormulaStateValidator
    implements ConstraintValidator<ValidFormulaState, WriteFormulaDto> {

  private static final String MESSAGE_KEY = "validation.formula.state";

  /**
   * Validates that the formula DTO is either in a new or existing state.
   *
   * @param dto the formula DTO to validate
   * @param context the validation context used to report validation failures
   * @return {@code true} if the DTO represents a valid state; {@code false}
   *     otherwise
   */
  @Override
  public boolean isValid(WriteFormulaDto dto, ConstraintValidatorContext context) {
    if (dto == null) return true;

    boolean hasId = dto.id() != null;
    boolean hasVersion = dto.version() != null;

    // Valid State 1: Existing Formula (Everything is present)
    // Valid State 2: New Formula (Only expression is present, id and version are null)
    if (hasId == hasVersion) {
      return true;
    }

    context.disableDefaultConstraintViolation();

    context.buildConstraintViolationWithTemplate(MESSAGE_KEY).addConstraintViolation();

    return false;
  }
}
