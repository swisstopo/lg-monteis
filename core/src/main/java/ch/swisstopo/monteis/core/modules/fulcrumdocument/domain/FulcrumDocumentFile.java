package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

// id is Fulcrum's attachment id, sizeBytes and storedAt stay null until the content is in storage
public record FulcrumDocumentFile(
    UUID id,
    UUID documentId,
    String fileName,
    String contentType,
    Long sizeBytes,
    OffsetDateTime storedAt,
    OffsetDateTime removedAt) {

  public boolean isStored() {
    return storedAt != null;
  }

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }
}
