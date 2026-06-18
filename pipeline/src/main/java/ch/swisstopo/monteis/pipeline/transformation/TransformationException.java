package ch.swisstopo.monteis.pipeline.transformation;

public class TransformationException extends RuntimeException {
    private final transient Object failedPayload;

    public TransformationException(String message, Object failedPayload) {
        super(message);
        this.failedPayload = failedPayload;
    }

    public TransformationException(String message, Throwable cause, Object failedPayload) {
        super(message, cause);
        this.failedPayload = failedPayload;
    }

    public Object getFailedPayload() {
        return failedPayload;
    }
}
