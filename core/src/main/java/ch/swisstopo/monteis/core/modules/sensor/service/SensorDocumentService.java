package ch.swisstopo.monteis.core.modules.sensor.service;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentUploads;
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

  // the filter chain only checks the write grant, the insert policy decides per sensor
  public SensorDocument upload(UUID sensorId, DocumentMetadata metadata, InputStream content) {
    String uploadedBy = currentUserProvider.requireCurrentUsername();
    sensorRepository.requireVisible(sensorId);
    DocumentKey key = new DocumentKey(sensorId, documentRepository.nextId());
    return DocumentUploads.storeThenRecord(
        storage,
        key,
        metadata,
        content,
        () -> documentRepository.create(key.documentId(), sensorId, metadata, uploadedBy));
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

  // S3 inside the transaction: a failed S3 delete rolls the row back, the document stays whole
  @Transactional
  public void delete(UUID sensorId, UUID documentId) {
    String deletedBy = currentUserProvider.requireCurrentUsername();
    SensorDocument document = documentRepository.getById(sensorId, documentId);
    documentRepository.delete(sensorId, documentId);
    storage.delete(document.key());
    log.info(
        "{} deleted document {} ({}) of sensor {}",
        deletedBy,
        documentId,
        document.metadata().fileName(),
        sensorId);
  }
}
