package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FulcrumDocumentService {

  private final FulcrumDocumentRepository repository;
  private final FulcrumDocumentStorage storage;

  public FulcrumDocumentService(
      FulcrumDocumentRepository repository, FulcrumDocumentStorage storage) {
    this.repository = repository;
    this.storage = storage;
  }

  public List<FulcrumDocument> getDocuments() {
    return repository.findAll();
  }

  // a file whose content is not in storage yet is as good as missing for the caller
  public FulcrumDocumentFile getStoredFile(UUID fileId) {
    FulcrumDocumentFile file = repository.getFileById(fileId);
    if (!file.isStored()) {
      throw new ObjectNotFoundException(FulcrumDocumentFile.class);
    }
    return file;
  }

  public InputStream openContent(FulcrumDocumentFile file) {
    return storage.load(file.id());
  }
}
