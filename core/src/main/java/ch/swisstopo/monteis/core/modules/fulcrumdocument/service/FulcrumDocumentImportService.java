package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownload;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

// storage first and the row after, like an experiment document upload: a failure in between leaves
// an object nobody sees, the next sync overwrites it under the same key
@Service
public class FulcrumDocumentImportService {

  // a longer tail after the last dot is part of the name, not an extension
  private static final int MAX_EXTENSION_LENGTH = 10;

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
      FulcrumDocumentFile file = fileOf(attachment, download.sizeBytes());
      storage.store(file, download.content());
      return file;
    }
  }

  private static FulcrumDocumentFile fileOf(FulcrumAttachmentReference attachment, long sizeBytes) {
    return new FulcrumDocumentFile(
        attachment.id(),
        DocumentMetadata.of(
            fileNameOf(attachment.name()), contentTypeOf(attachment.name()), sizeBytes));
  }

  // the extension survives, it is what the content type and the user's os go by
  private static String fileNameOf(String name) {
    if (name.length() <= DocumentMetadata.MAX_LENGTH) {
      return name;
    }
    int dot = name.lastIndexOf('.');
    String extension =
        dot > 0 && name.length() - dot <= MAX_EXTENSION_LENGTH ? name.substring(dot) : "";
    return name.substring(0, DocumentMetadata.MAX_LENGTH - extension.length()) + extension;
  }

  // Fulcrum serves every file as application/octet-stream, the browser could not show a pdf. only
  // the viewable types get theirs, html or svg would leave the api as active content otherwise
  private static String contentTypeOf(String fileName) {
    return MediaTypeFactory.getMediaType(fileName)
        .map(MediaType::toString)
        .filter(ViewableContentTypes::isViewable)
        .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }
}
