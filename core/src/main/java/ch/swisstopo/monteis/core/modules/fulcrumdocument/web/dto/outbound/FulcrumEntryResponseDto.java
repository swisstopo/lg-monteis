package ch.swisstopo.monteis.core.modules.fulcrumdocument.web.dto.outbound;

import java.util.List;
import java.util.UUID;

public record FulcrumDocumentResponseDto(
    UUID id,
    String number,
    String title,
    String authors,
    Integer year,
    String category,
    List<String> keywords,
    boolean availableInFulcrum,
    List<FulcrumDocumentFileResponseDto> files) {}
