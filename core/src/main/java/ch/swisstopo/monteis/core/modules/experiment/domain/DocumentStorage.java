package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.io.InputStream;
import java.util.UUID;

public interface DocumentStorage {
  void store(UUID experimentId, UUID documentId, DocumentMetadata metadata, InputStream content);

  InputStream load(ExperimentDocument document);

  void delete(UUID experimentId, UUID documentId);
}
