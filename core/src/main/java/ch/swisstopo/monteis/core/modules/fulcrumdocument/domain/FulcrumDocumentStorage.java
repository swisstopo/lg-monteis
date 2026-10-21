package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.io.InputStream;
import java.util.UUID;

// where the content of Fulcrum Documents is kept, apart from the experiment documents
public interface FulcrumDocumentStorage {

  // keyed by the attachment id. the content can only be read once, a failed upload is not retried
  void store(FulcrumDocumentFile file, InputStream content);

  // the caller closes the stream, it holds a connection
  InputStream load(UUID attachmentId);
}
