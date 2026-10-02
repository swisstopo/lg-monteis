package ch.swisstopo.monteis.core.modules.experiment.domain;

import java.io.InputStream;

/** Where the content of {@link ExperimentDocument}s is kept. */
public interface DocumentStorage {
  /** Stores {@code content}, which must be exactly {@link ExperimentDocument#sizeBytes()} long. */
  void store(ExperimentDocument document, InputStream content);

  /** Opens the stored content of {@code document}; the caller closes the stream. */
  InputStream load(ExperimentDocument document);
}
