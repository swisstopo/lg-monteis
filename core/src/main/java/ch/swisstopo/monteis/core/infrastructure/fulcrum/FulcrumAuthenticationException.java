package ch.swisstopo.monteis.core.infrastructure.fulcrum;

public class FulcrumAuthenticationException extends RuntimeException {

  public FulcrumAuthenticationException(String message) {
    super(message);
  }

  public FulcrumAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
