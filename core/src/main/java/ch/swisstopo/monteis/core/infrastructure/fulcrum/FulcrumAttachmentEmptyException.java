package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.util.UUID;

// skipped on purpose, not failed: would fail every sync until fixed in Fulcrum
public class FulcrumAttachmentEmptyException extends RuntimeException {

  public FulcrumAttachmentEmptyException(UUID attachmentId) {
    super("Fulcrum attachment %s is empty, it is not imported".formatted(attachmentId));
  }
}
