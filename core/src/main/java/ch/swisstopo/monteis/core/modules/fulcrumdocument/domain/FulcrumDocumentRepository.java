package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FulcrumDocumentRepository {

  List<FulcrumDocument> findAll();

  FulcrumDocument getById(UUID id);

  // inserts new documents and updates known ones, metadata and file alike. all of them count as
  // seen at seenAt and are no longer removed. only once their file is in storage
  void saveSeen(List<FulcrumDocument> documents, OffsetDateTime seenAt);

  // documents not seen at seenAt get removedAt, returns how many that hit
  int markRemovedUnlessSeenAt(OffsetDateTime seenAt);

  Optional<OffsetDateTime> findLastSeenAt();
}
