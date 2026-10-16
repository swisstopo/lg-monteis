package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.util.UUID;
import org.springframework.util.unit.DataSize;

// not a failed download: the file is skipped on purpose, every sync until someone shrinks it in
// Fulcrum or raises monteis.fulcrum.documents.max-file-size
public class FulcrumAttachmentTooLargeException extends RuntimeException {

  public FulcrumAttachmentTooLargeException(UUID attachmentId, DataSize maxSize) {
    super(
        "Fulcrum attachment %s is larger than %d MB, it is not imported"
            .formatted(attachmentId, maxSize.toMegabytes()));
  }
}
