package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import java.io.InputStream;
import java.util.UUID;

public interface FulcrumDocumentStorage {

  void store(FulcrumDocumentFile file, InputStream content);

  InputStream load(UUID attachmentId);
}
