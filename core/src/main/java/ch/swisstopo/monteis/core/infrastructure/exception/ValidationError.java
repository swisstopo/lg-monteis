package ch.swisstopo.monteis.core.infrastructure.exception;

import java.util.Map;

/**
 * Defines the contract for exceptions that represent structured validation
 * failures.
 *
 * <p>Implementations provide the information required by the API layer to
 * translate a validation failure into an {@code ErrorDto} response. The
 * contract intentionally only contains message-related information because
 * different validation scopes require different additional data:
 *
 * <ul>
 *   <li>Field-level validation errors provide the affected field and invalid
 *       value.</li>
 *   <li>Object-level validation errors provide contextual parameters describing
 *       the invalid object state.</li>
 * </ul>
 *
 * <p>This interface allows different validation exception types to share the
 * same error translation mechanism without forcing them into a common
 * inheritance hierarchy.
 */
public interface ValidationError {
  /**
   * Returns the internationalization (i18n) key used by clients to resolve
   * the localized validation message.
   *
   * @return the message key identifying the validation message
   */
  String getMessageKey();

  /**
   * Returns additional context required to render the validation message.
   *
   * <p>The content is message-specific and may contain values such as
   * boundaries, limits, or required settings.
   *
   * @return additional message parameters, never {@code null}
   */
  Map<String, Object> getParams();
}
