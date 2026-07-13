package ch.swisstopo.monteis.core.modules.sensor.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates the state of a {@code WriteFormulaDto}.
 *
 * <p>This constraint ensures that the identifier and optimistic locking
 * version are either both present or both absent:
 *
 * <ul>
 *   <li><strong>Existing formula:</strong> {@code id} and {@code version} are both provided.</li>
 *   <li><strong>New formula:</strong> {@code id} and {@code version} are both {@code null}.</li>
 * </ul>
 *
 * <p>This validation is required because nested DTOs cannot reliably leverage
 * create/update validation groups, making the entity state ambiguous without
 * additional validation.
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FormulaStateValidator.class)
public @interface ValidFormulaState {
  String message() default "validation.formula.state";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
