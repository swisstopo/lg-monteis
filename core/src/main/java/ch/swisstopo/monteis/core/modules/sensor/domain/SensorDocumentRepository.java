package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the metadata of {@link SensorDocument}s. Row-level security alone decides which
 * documents a caller reads (those of sensors they may read). Inserts are decided by the
 * sensor_documents_insert policy (can_write_an_experiment_of_sensor), the filter chain only checks
 * the experiment write grant. Nobody may update or delete a document.
 */
public interface SensorDocumentRepository {
  UUID nextId();

  /**
   * Persists the metadata of a new document under {@code id}, uploaded now by {@code uploadedBy}.
   *
   * @return the persisted document including its upload timestamp
   * @throws org.springframework.dao.PermissionDeniedDataAccessException if the caller may not
   *     upload documents to the sensor
   */
  SensorDocument create(UUID id, UUID sensorId, DocumentMetadata metadata, String uploadedBy);

  /**
   * Retrieves the documents of a sensor, newest first.
   *
   * @return the documents, empty if the sensor has none or is hidden from the caller
   */
  List<SensorDocument> findBySensorId(UUID sensorId);

  /**
   * Retrieves one document of a sensor.
   *
   * @throws ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException if the
   *     document does not exist, belongs to another sensor or is hidden from the caller
   */
  SensorDocument getById(UUID sensorId, UUID documentId);
}
