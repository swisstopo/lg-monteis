package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;

// only exists once its document is in storage. removedAt is set once Fulcrum stopped returning the
// entry, it stays in MONTEIS as a backup
public record FulcrumEntry(
    FulcrumEntryDetails details, FulcrumDocument document, OffsetDateTime removedAt) {

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }
}
