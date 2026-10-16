package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryWithDocuments;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FulcrumDocumentService {

  private final FulcrumEntryRepository entryRepository;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumDocumentStorage storage;

  public FulcrumDocumentService(
      FulcrumEntryRepository entryRepository,
      FulcrumDocumentRepository documentRepository,
      FulcrumDocumentStorage storage) {
    this.entryRepository = entryRepository;
    this.documentRepository = documentRepository;
    this.storage = storage;
  }

  public List<FulcrumEntryWithDocuments> getEntriesWithDocuments() {
    return entryRepository.findAllWithDocuments();
  }

  public FulcrumDocument getDocument(UUID documentId) {
    return documentRepository.getById(documentId);
  }

  public InputStream openContent(FulcrumDocument document) {
    return storage.load(document.id());
  }
}
