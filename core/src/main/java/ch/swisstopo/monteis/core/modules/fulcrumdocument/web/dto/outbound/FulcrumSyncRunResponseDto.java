package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FulcrumSyncRunResponseDto(
    UUID id,
    FulcrumSyncStatus status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String triggeredBy) {}
