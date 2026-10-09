package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExperimentDocument(
    UUID id,
    UUID experimentId,
    DocumentMetadata metadata,
    OffsetDateTime uploadedAt,
    String uploadedBy) {}
