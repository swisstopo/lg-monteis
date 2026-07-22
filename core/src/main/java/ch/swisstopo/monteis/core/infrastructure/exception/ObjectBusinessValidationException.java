package ch.swisstopo.monteis.core.infrastructure.exception;

import ch.swisstopo.monteis.core.infrastructure.error.ErrorDto;
import java.util.Map;

/**
 * Exception indicating that a business validation rule failed for an object as
 * a whole.
 *
 * <p>This exception is used when the invalid state cannot be attributed to a
 * single field but results from the relationship between multiple values or
 * from a violation of an object invariant.
 *
 * <p>Typical use cases include domain rules where an object is internally
 * inconsistent, for example when a lower bound is greater than an upper bound.
 * The API layer maps these exceptions to form-level {@link ErrorDto} responses
 * because no individual field can represent the complete cause of the failure.
 *
 * @see FieldBusinessValidationException
 */
public class ObjectBusinessValidationException extends RuntimeException implements ValidationError {
  /**
   * The i18n key used by clients to resolve the localized validation message.
   */
  private final String messageKey;

  /**
   * Additional values required to render the validation message.
   *
   * <p>Parameters typically contain contextual information about the invalid
   * object state, especially when multiple values are involved.
   */
  private final transient Map<String, Object> params;

  public ObjectBusinessValidationException(String messageKey, Map<String, Object> params) {
    super(messageKey);
    this.messageKey = messageKey;
    this.params = params != null ? params : Map.of();
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
