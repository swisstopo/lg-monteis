package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record FulcrumDocumentResponseDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    String number,
    String title,
    String authors,
    Integer year,
    String category,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> keywords,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean availableInFulcrum,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FulcrumDocumentFileResponseDto file) {}
