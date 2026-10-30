package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import io.swagger.v3.oas.annotations.media.Schema;

public record FulcrumDocumentFileResponseDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fileName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sizeBytes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean viewable) {}
