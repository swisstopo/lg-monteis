package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FulcrumDocumentRepository {

  List<FulcrumDocument> findAll();

  FulcrumDocument getById(UUID id);

  void saveSynced(FulcrumDocument document);

  void markRemovedFromFulcrum(Collection<UUID> documentIds, OffsetDateTime removedAt);
}
