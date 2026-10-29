package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

public interface FulcrumDocumentStorage {

  void store(FulcrumDocumentFile file, InputStream content, Duration timeout);

  InputStream load(UUID attachmentId);
}
