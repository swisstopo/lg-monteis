package ch.swisstopo.monteis.core.infrastructure.userdirectory;

/**
 * Keycloak answered, but the caller's token may not read the users asked for. With the realm set
 * up as intended this does not happen, so it points at a broken permission setup rather than at
 * the user.
 */
public class UserDirectoryAccessDeniedException extends UserDirectoryUnavailableException {

  public UserDirectoryAccessDeniedException(String message, Throwable cause) {
    super(message, cause);
  }
}
