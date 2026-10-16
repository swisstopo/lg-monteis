package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FulcrumDocumentService {

  private final FulcrumEntryRepository entryRepository;
  private final FulcrumDocumentStorage storage;

  public FulcrumDocumentService(
      FulcrumEntryRepository entryRepository, FulcrumDocumentStorage storage) {
    this.entryRepository = entryRepository;
    this.storage = storage;
  }

  public List<FulcrumEntry> getEntries() {
    return entryRepository.findAll();
  }

  public FulcrumDocument getDocument(UUID documentId) {
    return entryRepository.getDocumentById(documentId);
  }

  public InputStream openContent(FulcrumDocument document) {
    return storage.load(document.id());
  }
}
