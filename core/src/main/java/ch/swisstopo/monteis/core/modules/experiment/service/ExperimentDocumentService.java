package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentStorage;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocumentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentDocumentService {
  private final ExperimentRepository experimentRepository;
  private final ExperimentDocumentRepository documentRepository;
  private final DocumentStorage storage;
  private final CurrentUserProvider currentUserProvider;

  public ExperimentDocumentService(
      ExperimentRepository experimentRepository,
      ExperimentDocumentRepository documentRepository,
      DocumentStorage storage,
      CurrentUserProvider currentUserProvider) {
    this.experimentRepository = experimentRepository;
    this.documentRepository = documentRepository;
    this.storage = storage;
    this.currentUserProvider = currentUserProvider;
  }

  // insert first, so RLS rejects a forbidden upload before anything reaches the storage, and a
  // failed store rolls the insert back
  @Transactional
  public ExperimentDocument upload(
      UUID experimentId, DocumentMetadata metadata, InputStream content) {
    experimentRepository.requireVisible(experimentId);
    ExperimentDocument document =
        documentRepository.create(
            experimentId, metadata, currentUserProvider.requireCurrentUsername());
    storage.store(document, content);
    return document;
  }

  @Transactional(readOnly = true)
  public List<ExperimentDocument> getDocuments(UUID experimentId) {
    experimentRepository.requireVisible(experimentId);
    return documentRepository.findByExperimentId(experimentId);
  }

  public ExperimentDocument getDocument(UUID experimentId, UUID documentId) {
    return documentRepository.getById(experimentId, documentId);
  }

  /** Opens the stored content of {@code document}. The caller closes it, it holds a connection. */
  public InputStream openContent(ExperimentDocument document) {
    return storage.load(document);
  }
}
