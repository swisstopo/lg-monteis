package ch.swisstopo.monteis.core.modules.experiment.service;

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

  public ExperimentDocumentService(
      ExperimentRepository experimentRepository,
      ExperimentDocumentRepository documentRepository,
      DocumentStorage storage) {
    this.experimentRepository = experimentRepository;
    this.documentRepository = documentRepository;
    this.storage = storage;
  }

  // insert first, so RLS rejects a forbidden upload before anything reaches the storage, and a
  // failed store rolls the insert back
  @Transactional
  public ExperimentDocument upload(
      UUID experimentId, DocumentMetadata metadata, InputStream content) {
    experimentRepository.getById(experimentId);
    ExperimentDocument document = documentRepository.create(experimentId, metadata);
    storage.store(document, content);
    return document;
  }

  @Transactional(readOnly = true)
  public List<ExperimentDocument> getDocuments(UUID experimentId) {
    experimentRepository.getById(experimentId);
    return documentRepository.findByExperimentId(experimentId);
  }

  public DocumentDownload download(UUID experimentId, UUID documentId) {
    ExperimentDocument document = documentRepository.getById(experimentId, documentId);
    return new DocumentDownload(document, storage.load(document));
  }
}
