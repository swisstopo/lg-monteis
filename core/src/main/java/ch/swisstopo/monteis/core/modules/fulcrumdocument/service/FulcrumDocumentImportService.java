package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownload;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import org.springframework.stereotype.Service;

// storage first and the row after, like an experiment document upload: a failure in between leaves
// an object nobody sees, the next sync overwrites it under the same key
@Service
public class FulcrumDocumentImportService {

  private final FulcrumAttachmentDownloadClient downloadClient;
  private final FulcrumDocumentStorage storage;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumLiteratureMapper literatureMapper;
  private final FulcrumDocumentSyncProperties syncProperties;

  public FulcrumDocumentImportService(
      FulcrumAttachmentDownloadClient downloadClient,
      FulcrumDocumentStorage storage,
      FulcrumDocumentRepository documentRepository,
      FulcrumLiteratureMapper literatureMapper,
      FulcrumDocumentSyncProperties syncProperties) {
    this.downloadClient = downloadClient;
    this.storage = storage;
    this.documentRepository = documentRepository;
    this.literatureMapper = literatureMapper;
    this.syncProperties = syncProperties;
  }

  public void importFulcrumDocument(FulcrumLiteratureRow row) {
    FulcrumAttachmentReference attachment =
        row.firstAttachment()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Fulcrum Document %s has no file".formatted(row.recordId())));
    FulcrumDocumentFile file = copyIntoStorage(attachment);
    documentRepository.saveSynced(literatureMapper.toDocument(row, file));
  }

  // streamed from Fulcrum straight into storage. a failed upload is not retried from here, nothing
  // was saved, so the next sync imports the file again
  private FulcrumDocumentFile copyIntoStorage(FulcrumAttachmentReference attachment) {
    try (FulcrumAttachmentDownload download =
        downloadClient.download(attachment.id(), syncProperties.maxFileSize())) {
      FulcrumDocumentFile file =
          FulcrumDocumentFile.of(attachment.id(), attachment.name(), download.sizeBytes());
      storage.store(file, download.content());
      return file;
    }
  }
}
