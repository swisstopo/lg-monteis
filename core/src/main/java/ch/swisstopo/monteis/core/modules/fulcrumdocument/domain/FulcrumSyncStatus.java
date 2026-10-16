package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

// lastSuccessfulSyncAt is when the last sync without any failure started, none before the first
public record FulcrumSyncStatus(boolean running, @Nullable OffsetDateTime lastSuccessfulSyncAt) {}
