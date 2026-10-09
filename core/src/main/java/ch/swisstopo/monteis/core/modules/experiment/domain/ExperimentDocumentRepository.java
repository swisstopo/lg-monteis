package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.util.List;
import java.util.UUID;

public interface ExperimentDocumentRepository {
  ExperimentDocument create(UUID experimentId, DocumentMetadata metadata, String uploadedBy);

  List<ExperimentDocument> findByExperimentId(UUID experimentId);

  ExperimentDocument getById(UUID experimentId, UUID documentId);
}
