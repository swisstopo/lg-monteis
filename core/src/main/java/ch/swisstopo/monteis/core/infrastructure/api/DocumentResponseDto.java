package ch.swisstopo.monteis.core.infrastructure.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponseDto(
    UUID id,
    String fileName,
    String contentType,
    long sizeBytes,
    boolean viewable,
    OffsetDateTime uploadedAt,
    String uploadedBy) {}
