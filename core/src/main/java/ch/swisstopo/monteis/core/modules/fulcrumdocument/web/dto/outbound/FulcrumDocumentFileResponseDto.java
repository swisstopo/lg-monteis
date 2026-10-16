package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

public record FulcrumDocumentFileResponseDto(
    String fileName, String contentType, long sizeBytes, boolean viewable) {}
