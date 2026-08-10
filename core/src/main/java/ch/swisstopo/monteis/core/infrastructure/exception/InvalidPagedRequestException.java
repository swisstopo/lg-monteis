package ch.swisstopo.monteis.core.infrastructure.exception;

/**
 * Indicates a paged request (ag-grid sort/filter model, or the columns it references) could not
 * be parsed or resolved into a valid {@code PagedRequest}.
 *
 * <p>Distinct from {@link ObjectBusinessValidationException}/{@link
 * FieldBusinessValidationException}: those represent business-rule violations on well-formed
 * input, whereas this represents a malformed/unsupported request shape - the paging equivalent of
 * an unparseable request body.
 */
public class InvalidPagedRequestException extends RuntimeException {
  public InvalidPagedRequestException(String message) {
    super(message);
  }

  public InvalidPagedRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
