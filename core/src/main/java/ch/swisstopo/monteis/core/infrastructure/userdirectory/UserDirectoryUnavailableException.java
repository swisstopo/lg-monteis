package ch.swisstopo.monteis.core.infrastructure.userdirectory;

public class UserDirectoryUnavailableException extends RuntimeException {

  public static final String MESSAGE_KEY = "error.user-directory.unavailable";

  public UserDirectoryUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
