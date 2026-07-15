package ch.swisstopo.monteis.core.infrastructure.exception;

import ch.swisstopo.monteis.core.infrastructure.error.ErrorDto;
import java.util.Map;

/**
 * Exception indicating that a business validation rule failed for a specific
 * input field.
 *
 * <p>This exception is used when a validation failure can be associated with a
 * single value provided by the client. The contained field information allows
 * the API layer to map the exception to a field-level {@link ErrorDto} response,
 * enabling clients to display the error directly on the corresponding input
 * control.
 *
 * <p>Typical use cases include invalid user-provided values where the complete
 * object state is not invalid, but one specific field does not satisfy a
 * business rule.
 *
 * @see ObjectBusinessValidationException
 */
public class FieldBusinessValidationException extends RuntimeException implements ValidationError {

  /**
   * The name of the affected input field.
   */
  private final String field;

  /**
   * The value that caused the validation failure.
   *
   * <p>This value is useful for logging, debugging, and reproducing invalid
   * states.
   */
  private final transient Object actualValue;

  /**
   * The i18n key used by clients to resolve the localized validation message.
   */
  private final String messageKey;

  /**
   * Additional values required to render the validation message.
   */
  private final transient Map<String, Object> params;

  public FieldBusinessValidationException(
      String field, Object actualValue, String messageKey, Map<String, Object> params) {
    super(messageKey);
    this.field = field;
    this.actualValue = actualValue;
    this.messageKey = messageKey;
    this.params = params;
  }

  public String getField() {
    return field;
  }

  public Object getActualValue() {
    return actualValue;
  }

  @Override
  public String getMessageKey() {
    return messageKey;
  }

  @Override
  public Map<String, Object> getParams() {
    return params;
  }
}
