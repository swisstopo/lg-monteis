package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExperimentDocumentResponseDto(
    UUID id,
    UUID experimentId,
    String fileName,
    String contentType,
    long sizeBytes,
    boolean viewable,
    OffsetDateTime uploadedAt,
    String uploadedBy) {}
