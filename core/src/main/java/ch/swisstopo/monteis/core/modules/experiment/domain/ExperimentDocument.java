package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A file uploaded to an experiment. Its content lives in {@link DocumentStorage}.
 *
 * @param uploadedBy the uploader's username at upload time. experiments and the audit log keep
 *     the subject handle instead, but the document list shows this name and there is no user
 *     directory to resolve a handle into one
 */
public record ExperimentDocument(
    UUID id,
    UUID experimentId,
    DocumentMetadata metadata,
    OffsetDateTime uploadedAt,
    String uploadedBy) {}
