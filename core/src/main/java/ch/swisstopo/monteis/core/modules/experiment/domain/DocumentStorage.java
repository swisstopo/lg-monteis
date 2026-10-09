package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.io.InputStream;
import java.util.UUID;

/**
 * Where the content of {@link ExperimentDocument}s is kept, the database only holds their
 * metadata.
 */
public interface DocumentStorage {
  /**
   * Stores {@code content}, which must be exactly {@link DocumentMetadata#sizeBytes()} long. The
   * length goes to the storage up front, the stream is not buffered to count it.
   */
  void store(UUID experimentId, UUID documentId, DocumentMetadata metadata, InputStream content);

  /** Opens the stored content of {@code document}. The caller closes it, it holds a connection. */
  InputStream load(ExperimentDocument document);

  void delete(UUID experimentId, UUID documentId);
}
