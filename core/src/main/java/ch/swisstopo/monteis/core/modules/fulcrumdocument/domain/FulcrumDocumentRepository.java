package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface FulcrumDocumentRepository {

  List<FulcrumDocument> findAll();

  FulcrumDocumentFile getFileById(UUID fileId);

  // inserts new documents and files and updates the metadata of known ones, never their stored
  // content. everything passed counts as seen at seenAt and is no longer removed
  void saveSeen(List<FulcrumDocument> documents, OffsetDateTime seenAt);

  // documents and files not seen at seenAt get removedAt, returns how many documents that hit
  int markRemovedUnlessSeenAt(OffsetDateTime seenAt);

  List<FulcrumDocumentFile> findFilesToStore();

  void markStored(UUID fileId, long sizeBytes);
}
