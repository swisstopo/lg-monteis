package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.util.UUID;
import org.springframework.util.unit.DataSize;

// skipped on purpose, not failed: would fail every sync until shrunk in Fulcrum or
// monteis.fulcrum.documents.max-file-size raised
public class FulcrumAttachmentTooLargeException extends RuntimeException {

  public FulcrumAttachmentTooLargeException(UUID attachmentId, DataSize maxSize) {
    super(
        "Fulcrum attachment %s is larger than %d MB, it is not imported"
            .formatted(attachmentId, maxSize.toMegabytes()));
  }
}
