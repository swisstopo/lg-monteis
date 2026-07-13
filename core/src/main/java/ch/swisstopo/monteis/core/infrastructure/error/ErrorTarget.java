package ch.swisstopo.monteis.core.infrastructure.error;

/**
 * Defines the target scope of an API error and determines how clients should
 * present the error to users.
 *
 * <p>The target distinguishes between errors affecting the entire request,
 * errors affecting the validity of a complete object or form, and errors
 * associated with a specific input field.
 */
public enum ErrorTarget {

  /**
   * Represents a system-level error unrelated to user input.
   *
   * <p>These errors are typically displayed as global notifications or
   * toasts. Examples include unexpected backend failures, unavailable
   * dependencies, or other technical problems.
   */
  GLOBAL,

  /**
   * Represents an error affecting the validity of an entire object or form.
   *
   * <p>These errors cannot be attributed to a single field and usually result
   * from invalid relationships between multiple values or violations of
   * business rules. Clients typically display these errors above the form.
   *
   * <p>Examples include invalid bounds where {@code lowerBound} is greater
   * than {@code upperBound}, or inconsistent states involving multiple fields.
   */
  FORM,

  /**
   * Represents an error associated with a specific input field.
   *
   * <p>These errors are caused by an invalid value of a single field and are
   * typically displayed directly next to the corresponding input control.
   *
   * <p>Examples include invalid formats, missing required values, or malformed
   * user input for a specific field.
   */
  FIELD
}
