package ch.swisstopo.monteis.core.infrastructure.userdirectory;

/** Keycloak refused what core asked with the caller's token, the realm's permissions are off. */
public class UserDirectoryDeniedException extends RuntimeException {

  public static final String MESSAGE_KEY = "error.user-directory.denied";

  public UserDirectoryDeniedException(String message) {
    super(message);
  }
}
