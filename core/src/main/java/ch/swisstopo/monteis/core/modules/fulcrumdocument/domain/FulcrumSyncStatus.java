package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;

// lastSyncedAt is when the last sync whose Fulcrum query succeeded started, null before the first
public record FulcrumSyncStatus(boolean running, OffsetDateTime lastSyncedAt) {}
