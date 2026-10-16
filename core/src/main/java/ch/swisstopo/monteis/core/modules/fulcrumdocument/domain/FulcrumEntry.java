package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

// only exists once its document is in storage. removedAt is set once Fulcrum stopped returning the
// entry, it stays in MONTEIS as a backup
public record FulcrumEntry(
    FulcrumCitation citation, FulcrumDocument document, @Nullable OffsetDateTime removedAt) {

  public Optional<OffsetDateTime> removedFromFulcrumAt() {
    return Optional.ofNullable(removedAt);
  }

  public boolean isAvailableInFulcrum() {
    return removedAt == null;
  }
}
