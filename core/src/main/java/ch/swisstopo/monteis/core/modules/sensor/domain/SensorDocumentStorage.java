package ch.swisstopo.monteis.core.modules.sensor.domain;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.io.InputStream;

/**
 * Where the content of {@link SensorDocument}s is kept, the database only holds their metadata.
 */
public interface SensorDocumentStorage {
  /**
   * Stores {@code content}, which must be exactly {@link DocumentMetadata#sizeBytes()} long. The
   * length goes to the storage up front, the stream is not buffered to count it.
   */
  void store(DocumentKey key, DocumentMetadata metadata, InputStream content);

  /** Opens the stored content under {@code key}. The caller closes it, it holds a connection. */
  InputStream load(DocumentKey key);

  void delete(DocumentKey key);
}
