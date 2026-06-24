package ch.swisstopo.monteis.pipeline.transformation;

public class TransformationException extends RuntimeException {

  private final Double failedPayload;

  public TransformationException(String message, Throwable cause) {
    this(message, cause, null);
  }

  public TransformationException(String message, Throwable cause, Double failedPayload) {
    super(message, cause);
    this.failedPayload = failedPayload;
  }

  public Double getFailedPayload() {
    return failedPayload;
  }
}
