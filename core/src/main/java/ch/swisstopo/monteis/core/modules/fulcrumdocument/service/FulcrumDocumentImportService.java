package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownload;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentComparison.NewFile;
import java.time.Duration;
import org.springframework.stereotype.Service;

// storage before row (like experiment uploads): failure between leaves invisible object, next
// sync overwrites same key
@Service
class FulcrumDocumentImportService {

  private final FulcrumAttachmentDownloadClient downloadClient;
  private final FulcrumDocumentStorage storage;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumLiteratureMapper literatureMapper;
  private final FulcrumDocumentSyncProperties syncProperties;

  FulcrumDocumentImportService(
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

  void importFulcrumDocument(NewFile newFile, Duration timeLeft) {
    FulcrumDocumentFile file = copyIntoStorage(newFile.attachment(), timeLeft);
    documentRepository.saveSynced(literatureMapper.toDocument(newFile.row(), file));
  }

  private FulcrumDocumentFile copyIntoStorage(
      FulcrumAttachmentReference attachment, Duration timeLeft) {
    try (FulcrumAttachmentDownload download =
        downloadClient.download(attachment.id(), syncProperties.maxFileSize())) {
      FulcrumDocumentFile file =
          FulcrumDocumentFile.of(attachment.id(), attachment.name(), download.sizeBytes());
      // download streams through upload, so this caps both
      storage.store(file, download.content(), timeLeft);
      return file;
    }
  }
}
