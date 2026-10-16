package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.time.OffsetDateTime;
import java.util.UUID;

// one file of a Fulcrum entry, its content lives in FulcrumDocumentStorage. id is Fulcrum's
// attachment id. removedAt is set once no entry in Fulcrum lists it anymore
public record FulcrumDocument(
    UUID id,
    UUID entryId,
    DocumentMetadata metadata,
    OffsetDateTime storedAt,
    OffsetDateTime removedAt) {

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }
}
