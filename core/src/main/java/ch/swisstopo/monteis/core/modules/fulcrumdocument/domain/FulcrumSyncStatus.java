package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

public record FulcrumSyncStatus(boolean running, @Nullable OffsetDateTime lastSuccessfulSyncAt) {}
