package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocumentRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocumentStorage;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensorDocumentService {

  private static final Logger log = LoggerFactory.getLogger(SensorDocumentService.class);

  private final SensorRepository sensorRepository;
  private final SensorDocumentRepository documentRepository;
  private final SensorDocumentStorage storage;
  private final CurrentUserProvider currentUserProvider;

  public SensorDocumentService(
      SensorRepository sensorRepository,
      SensorDocumentRepository documentRepository,
      SensorDocumentStorage storage,
      CurrentUserProvider currentUserProvider) {
    this.sensorRepository = sensorRepository;
    this.documentRepository = documentRepository;
    this.storage = storage;
    this.currentUserProvider = currentUserProvider;
  }

  // no transaction around the upload, as for experiment documents
  public SensorDocument upload(UUID sensorId, DocumentMetadata metadata, InputStream content) {
    String uploadedBy = currentUserProvider.requireCurrentUsername();
    sensorRepository.requireVisible(sensorId);
    DocumentKey key = new DocumentKey(sensorId, documentRepository.nextId());
    storage.store(key, metadata, content);
    try {
      return documentRepository.create(key.documentId(), sensorId, metadata, uploadedBy);
    } catch (RuntimeException e) {
      deleteStored(key, e);
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public List<SensorDocument> getDocuments(UUID sensorId) {
    sensorRepository.requireVisible(sensorId);
    return documentRepository.findBySensorId(sensorId);
  }

  public SensorDocument getDocument(UUID sensorId, UUID documentId) {
    return documentRepository.getById(sensorId, documentId);
  }

  public InputStream openContent(SensorDocument document) {
    return storage.load(document.key());
  }

  private void deleteStored(DocumentKey key, RuntimeException cause) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      cause.addSuppressed(e);
      log.warn(
          "could not delete the orphaned document {} of sensor {}",
          key.documentId(),
          key.ownerId(),
          e);
    }
  }
}
