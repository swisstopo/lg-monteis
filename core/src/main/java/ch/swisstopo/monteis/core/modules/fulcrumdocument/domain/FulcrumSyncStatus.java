package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

// lastSyncedAt is when the last sync whose Fulcrum query succeeded started, none before the first
public record FulcrumSyncStatus(boolean running, @Nullable OffsetDateTime lastSyncedAt) {

  public static FulcrumSyncStatus of(boolean running, Optional<OffsetDateTime> lastSyncedAt) {
    return new FulcrumSyncStatus(running, lastSyncedAt.orElse(null));
  }
}
