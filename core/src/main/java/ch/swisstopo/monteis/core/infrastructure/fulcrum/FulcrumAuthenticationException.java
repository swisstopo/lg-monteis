package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

public class FulcrumAuthenticationException extends RuntimeException {

  // both Fulcrum clients send the token, a 401 or 403 always means it was rejected
  static RuntimeException translateRejectedToken(RestClientResponseException e) {
    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
      return new FulcrumAuthenticationException(
          "Fulcrum rejected the configured API token with %s".formatted(e.getStatusCode()), e);
    }
    return e;
  }

  public FulcrumAuthenticationException(String message) {
    super(message);
  }

  /**
   * @param message log-only detail, e.g. which token check failed
   * @param cause the rejected HTTP call, kept for the stack trace in the log
   */
  public FulcrumAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
