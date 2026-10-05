package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A file uploaded to an experiment. Its content lives in {@link DocumentStorage}.
 *
 * @param uploadedBy the uploader's username
 */
public record ExperimentDocument(
    UUID id,
    UUID experimentId,
    DocumentMetadata metadata,
    OffsetDateTime uploadedAt,
    String uploadedBy) {}
