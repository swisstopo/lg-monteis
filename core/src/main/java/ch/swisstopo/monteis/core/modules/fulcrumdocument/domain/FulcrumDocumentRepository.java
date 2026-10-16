package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.util.UUID;

public interface FulcrumDocumentRepository {

  // only once the content is in storage
  FulcrumDocument create(UUID id, UUID entryId, DocumentMetadata metadata);

  FulcrumDocument getById(UUID id);

  // after the entries are saved: a document no entry in Fulcrum lists anymore gets removedAt, one
  // listed again loses it, and one Fulcrum moved to another entry follows it
  void reconcileWithEntries();
}
