package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import java.util.UUID;

public record FulcrumDocumentResponseDto(
    UUID id, String fileName, String contentType, long sizeBytes, boolean viewable) {}
