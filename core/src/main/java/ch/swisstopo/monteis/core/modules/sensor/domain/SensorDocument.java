package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A file uploaded to a sensor. Its content lives in {@link SensorDocumentStorage}.
 *
 * @param uploadedBy the uploader's username at upload time, as for experiment documents
 */
public record SensorDocument(
    UUID id,
    UUID sensorId,
    DocumentMetadata metadata,
    OffsetDateTime uploadedAt,
    String uploadedBy) {

  public DocumentKey key() {
    return new DocumentKey(sensorId, id);
  }
}
