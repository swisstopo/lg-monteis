package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

// where the content of Fulcrum Documents is kept, apart from the experiment documents
public interface FulcrumDocumentStorage {

  // keyed by the attachment id. from a local file rather than a stream, so a failed attempt can be
  // retried from the start
  void store(UUID attachmentId, String contentType, Path content);

  // the caller closes the stream, it holds a connection
  InputStream load(UUID attachmentId);
}
