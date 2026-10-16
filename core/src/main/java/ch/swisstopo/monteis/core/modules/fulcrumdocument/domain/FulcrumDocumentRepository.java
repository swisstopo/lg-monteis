package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FulcrumDocumentRepository {

  List<FulcrumDocument> findAll();

  FulcrumDocument getById(UUID id);

  // inserts new documents and updates known ones, metadata and file alike, as Fulcrum delivered
  // them in the sync started at syncedAt. a removed one becomes available again. only once their
  // file is in storage
  void saveSynced(List<FulcrumDocument> documents, OffsetDateTime syncedAt);

  void markRemovedFromFulcrum(Collection<UUID> documentIds);

  // when the last sync that reached Fulcrum started, its file imports may still have failed
  Optional<OffsetDateTime> findLastSuccessfulSyncAt();
}
