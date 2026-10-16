package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface FulcrumEntryRepository {

  List<FulcrumEntry> findAll();

  FulcrumDocument getDocumentById(UUID documentId);

  Map<UUID, UUID> findDocumentIdsByEntry();

  // updates the details of known entries, leaves their document as is. all of them count as seen
  // at seenAt and are no longer removed
  void saveSeenDetails(List<FulcrumEntryDetails> details, OffsetDateTime seenAt);

  // inserts the entry, or switches a known one to its new document, only once that is in storage
  void saveWithDocument(
      FulcrumEntryDetails details, FulcrumDocument document, OffsetDateTime seenAt);

  // entries not seen at seenAt get removedAt, returns how many that hit
  int markRemovedUnlessSeenAt(OffsetDateTime seenAt);

  Optional<OffsetDateTime> findLastSeenAt();
}
