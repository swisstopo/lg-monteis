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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentDocumentService {

  private static final Logger log = LoggerFactory.getLogger(ExperimentDocumentService.class);

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

  // no transaction around the upload, it would block a db connection for as long as S3 takes.
  // S3 first and the insert after, so a failure in between leaves an object nobody sees instead of
  // a document without content. no write check before S3, the filter chain already did it and the
  // insert policy does it again
  public ExperimentDocument upload(
      UUID experimentId, DocumentMetadata metadata, InputStream content) {
    String uploadedBy = currentUserProvider.requireCurrentUsername();
    experimentRepository.requireVisible(experimentId);
    UUID documentId = documentRepository.nextId();
    storage.store(experimentId, documentId, metadata, content);
    try {
      return documentRepository.create(documentId, experimentId, metadata, uploadedBy);
    } catch (RuntimeException e) {
      deleteStored(experimentId, documentId, e);
      throw e;
    }
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
    return storage.load(document);
  }

  private void deleteStored(UUID experimentId, UUID documentId, RuntimeException cause) {
    try {
      storage.delete(experimentId, documentId);
    } catch (RuntimeException e) {
      cause.addSuppressed(e);
      log.warn(
          "could not delete the orphaned document {} of experiment {}",
          documentId,
          experimentId,
          e);
    }
  }
}
