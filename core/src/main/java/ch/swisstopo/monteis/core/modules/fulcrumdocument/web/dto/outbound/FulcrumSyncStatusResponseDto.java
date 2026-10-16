package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import java.time.OffsetDateTime;

public record FulcrumSyncStatusResponseDto(boolean running, OffsetDateTime lastSyncedAt) {}
