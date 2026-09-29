package ch.swisstopo.monteis.core.infrastructure.exception;

/**
 * Indicates that a requested object does not exist or is hidden from the caller by row-level
 * security. Both cases are deliberately indistinguishable: the API maps this exception to 404
 * {@code object.not-found} with no further detail, so probing ids reveals nothing about which
 * objects exist (BR4.11, NFR1.4).
 */
public class ObjectNotFoundException extends RuntimeException {

  public static final String MESSAGE_KEY = "object.not-found";

  /**
   * @param objectType the kind of object that was looked up, for logs only; never sent to the
   *     client
   */
  public ObjectNotFoundException(String objectType) {
    super(objectType + " not found or not visible");
  }
}
