package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import java.util.UUID;

public record FulcrumDocumentFileResponseDto(
    UUID id,
    String fileName,
    String contentType,
    boolean viewable,
    boolean stored,
    boolean availableInFulcrum) {}
