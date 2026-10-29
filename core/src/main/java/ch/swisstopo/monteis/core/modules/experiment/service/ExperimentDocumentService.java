package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentUploads;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocumentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocumentStorage;
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
  private final ExperimentDocumentStorage storage;
  private final CurrentUserProvider currentUserProvider;

  public ExperimentDocumentService(
      ExperimentRepository experimentRepository,
      ExperimentDocumentRepository documentRepository,
      ExperimentDocumentStorage storage,
      CurrentUserProvider currentUserProvider) {
    this.experimentRepository = experimentRepository;
    this.documentRepository = documentRepository;
    this.storage = storage;
    this.currentUserProvider = currentUserProvider;
  }

  // no write check before S3, the filter chain already did it and the insert policy does it again
  public ExperimentDocument upload(
      UUID experimentId, DocumentMetadata metadata, InputStream content) {
    String uploadedBy = currentUserProvider.requireCurrentUsername();
    experimentRepository.requireVisible(experimentId);
    DocumentKey key = new DocumentKey(experimentId, documentRepository.nextId());
    return DocumentUploads.storeThenRecord(
        storage,
        key,
        metadata,
        content,
        () -> documentRepository.create(key.documentId(), experimentId, metadata, uploadedBy));
  }

  @Transactional(readOnly = true)
  public List<ExperimentDocument> getDocuments(UUID experimentId) {
    experimentRepository.requireVisible(experimentId);
    return documentRepository.findByExperimentId(experimentId);
  }

  public ExperimentDocument getDocument(UUID experimentId, UUID documentId) {
    return documentRepository.getById(experimentId, documentId);
  }

  public InputStream openContent(ExperimentDocument document) {
    return storage.load(document.key());
  }
}
