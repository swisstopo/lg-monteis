package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the metadata of {@link ExperimentDocument}s. Row-level security alone decides
 * which documents a caller reads (those of experiments they may read). Inserts are checked twice:
 * the filter chain and the experiment_documents_insert policy both require write access to the
 * experiment. Nobody may update or delete a document.
 */
public interface ExperimentDocumentRepository {
  /**
   * Persists the metadata of a new document, uploaded now by {@code uploadedBy}.
   *
   * @return the persisted document including its generated id and upload timestamp
   * @throws org.springframework.dao.PermissionDeniedDataAccessException if the caller may not
   *     write the experiment
   */
  ExperimentDocument create(UUID experimentId, DocumentMetadata metadata, String uploadedBy);

  /**
   * Retrieves the documents of an experiment, newest first.
   *
   * @return the documents, empty if the experiment has none or is hidden from the caller
   */
  List<ExperimentDocument> findByExperimentId(UUID experimentId);

  /**
   * Retrieves one document of an experiment.
   *
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     document does not exist, belongs to another experiment or is hidden from the caller
   */
  ExperimentDocument getById(UUID experimentId, UUID documentId);
}
