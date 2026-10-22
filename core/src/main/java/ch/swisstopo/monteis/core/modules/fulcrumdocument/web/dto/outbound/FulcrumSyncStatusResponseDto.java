package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record FulcrumSyncStatusResponseDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean running,
    OffsetDateTime lastSuccessfulSyncAt) {}
