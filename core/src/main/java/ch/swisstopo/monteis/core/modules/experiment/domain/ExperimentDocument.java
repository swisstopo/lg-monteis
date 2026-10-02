package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadata of a file uploaded to an experiment. The file content itself lives in {@link
 * DocumentStorage}.
 *
 * @param uploadedBy the uploader's username
 */
public record ExperimentDocument(
    UUID id,
    UUID experimentId,
    String fileName,
    String contentType,
    long sizeBytes,
    OffsetDateTime uploadedAt,
    String uploadedBy) {}
