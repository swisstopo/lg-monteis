package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thrown when Fulcrum rejects the configured API token with 401/403. A missing token never gets
 * here, {@link FulcrumProperties} refuses to start without one while Fulcrum is enabled.
 *
 * <p>{@code GlobalErrorControllerAdvice} maps this to 502 with the generic {@code
 * error.system.internal} message and an error id. The message here is written to the log under
 * that id and never reaches the user, so it should name the configuration problem, not the record
 * being saved.
 */
public class FulcrumAuthenticationException extends RuntimeException {

  // both Fulcrum clients send the token, a 401 or 403 always means it was rejected
  static RuntimeException translateRejectedToken(RestClientResponseException e) {
    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
      return new FulcrumAuthenticationException(
          "Fulcrum rejected the configured API token with %s".formatted(e.getStatusCode()), e);
    }
    return e;
  }

  /**
   * @param message log-only detail, e.g. which token check failed
   */
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
