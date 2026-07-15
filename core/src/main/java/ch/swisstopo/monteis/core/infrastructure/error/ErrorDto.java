package ch.swisstopo.monteis.core.infrastructure.error;

import java.util.Map;

/**
 * Represents a structured error response returned to API clients.
 *
 * <p>An error has a {@link ErrorTarget} describing where the client should
 * present the error:
 *
 * <ul>
 *   <li>{@link ErrorTarget#GLOBAL}: A system-level error unrelated to user
 *       input. These errors are typically displayed as notifications or
 *       toasts.</li>
 *   <li>{@link ErrorTarget#FORM}: An error affecting the validity of an entire
 *       object or form. These errors occur when multiple values are invalid in
 *       combination or when an object violates a business rule.</li>
 *   <li>{@link ErrorTarget#FIELD}: An error associated with a specific input
 *       field. These errors are typically displayed directly next to the
 *       affected field.</li>
 * </ul>
 *
 * <p>The DTO intentionally separates the validation target from the technical
 * source of the error. This allows different backend validation mechanisms,
 * such as Bean Validation, business validation, and infrastructure error
 * handling, to expose a consistent contract to clients.
 *
 * @param target defines how and where the client should present the error
 * @param field the affected field name when {@link ErrorTarget#FIELD} is used.
 *     For form-level and global errors this value is {@code null}.
 * @param actualValue the value that caused the validation failure when the
 *     error targets a specific field. This value is useful for debugging,
 *     logging, and reproducing invalid input. It is {@code null} for form-level
 *     and global errors where no single value caused the violation.
 * @param messageKey the internationalization (i18n) key used by clients to
 *     resolve the localized error message.
 * @param params additional context required to render the error message.
 *     Parameters are message-specific and may contain values such as bounds,
 *     limits, or required settings.
 */
public record ErrorDto(
    ErrorTarget target,
    String field,
    Object actualValue,
    String messageKey,
    Map<String, Object> params) {

  /**
   * Creates an error representing an unexpected system failure.
   *
   * <p>Global errors are not caused by invalid user input and should usually
   * be displayed as a notification or toast.
   *
   * @param params additional context for the error message, such as an error
   *     identifier used for log correlation.
   * @return a global system error DTO
   */
  public static ErrorDto global(Map<String, Object> params) {
    return new ErrorDto(ErrorTarget.GLOBAL, null, null, "error.system.internal", params);
  }

  /**
   * Creates an error affecting the validity of a complete form or object.
   *
   * <p>Form errors are used when the validation failure cannot be attributed
   * to a single field, for example when multiple values are inconsistent with
   * each other or when a domain invariant is violated.
   *
   * @param messageKey the i18n key identifying the validation message
   * @param params additional context required to render the message
   * @return a form-level validation error DTO
   */
  public static ErrorDto form(String messageKey, Map<String, Object> params) {
    return new ErrorDto(ErrorTarget.FORM, null, null, messageKey, params);
  }

  /**
   * Creates an error affecting a specific input field.
   *
   * <p>Field errors are used when the validation failure can be directly
   * associated with one user-provided value. The client can use the field
   * name to display the message next to the corresponding input control.
   *
   * @param field the name of the affected field
   * @param actualValue the invalid value provided for the field
   * @param messageKey the i18n key identifying the validation message
   * @param params additional context required to render the message
   * @return a field-level validation error DTO
   */
  public static ErrorDto field(
      String field, Object actualValue, String messageKey, Map<String, Object> params) {
    return new ErrorDto(ErrorTarget.FIELD, field, actualValue, messageKey, params);
  }
}
