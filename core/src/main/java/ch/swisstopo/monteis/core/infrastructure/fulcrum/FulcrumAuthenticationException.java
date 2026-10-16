package ch.swisstopo.monteis.core.infrastructure.fulcrum;

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
