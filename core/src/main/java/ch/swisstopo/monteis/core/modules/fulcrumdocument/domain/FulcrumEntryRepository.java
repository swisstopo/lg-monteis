package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.List;

public interface FulcrumEntryRepository {

  List<FulcrumEntryWithDocuments> findAllWithDocuments();

  // inserts new entries and updates known ones, everything passed counts as seen at seenAt and is
  // no longer removed
  void saveSeen(List<FulcrumEntry> entries, OffsetDateTime seenAt);

  // entries not seen at seenAt get removedAt, returns how many that hit
  int markRemovedUnlessSeenAt(OffsetDateTime seenAt);

  // attachments of entries still in Fulcrum that have no FulcrumDocument yet
  List<FulcrumAttachment> findAttachmentsToStore();
}
