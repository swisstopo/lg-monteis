package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FulcrumDocumentRepository {

  List<FulcrumDocument> findAll();

  FulcrumDocument getById(UUID id);

  // inserts a new document or updates a known one, metadata and file alike, as Fulcrum delivered
  // it. a removed one becomes available again. only once its file is in storage
  void saveSynced(FulcrumDocument document);

  void markRemovedFromFulcrum(Collection<UUID> documentIds, OffsetDateTime removedAt);
}
