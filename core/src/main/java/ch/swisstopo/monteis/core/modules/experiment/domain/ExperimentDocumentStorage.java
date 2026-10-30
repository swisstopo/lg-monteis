package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentStorage;

/**
 * Where the content of {@link ExperimentDocument}s is kept, the database only holds their
 * metadata.
 */
public interface ExperimentDocumentStorage extends DocumentStorage {}
