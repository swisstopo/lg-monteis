package ch.swisstopo.monteis.core.infrastructure.userdirectory;

public class UserDirectoryDeniedException extends RuntimeException {

  public static final String MESSAGE_KEY = "error.user-directory.denied";

  public UserDirectoryDeniedException(String message) {
    super(message);
  }
}
