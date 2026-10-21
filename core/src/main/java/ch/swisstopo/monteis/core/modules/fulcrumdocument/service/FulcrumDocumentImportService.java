package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

// storage first and the row after, like an experiment document upload: a failure in between leaves
// an object nobody sees, the next sync overwrites it under the same key
@Service
public class FulcrumDocumentImportService {

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentImportService.class);

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
    try {
      Path download = Files.createTempFile("fulcrum-document-", ".download");
      try {
        downloadTo(download, attachment);
        FulcrumDocumentFile file = fileOf(attachment, download);
        storage.store(file.attachmentId(), file.metadata().contentType(), download);
        documentRepository.saveSynced(literatureMapper.toDocument(row, file));
      } finally {
        deleteQuietly(download);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void downloadTo(Path download, FulcrumAttachmentReference attachment) throws IOException {
    try (InputStream content =
        downloadClient.download(attachment.id(), syncProperties.maxFileSize())) {
      Files.copy(content, download, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static FulcrumDocumentFile fileOf(FulcrumAttachmentReference attachment, Path download)
      throws IOException {
    return new FulcrumDocumentFile(
        attachment.id(),
        DocumentMetadata.of(
            fileNameOf(attachment.name()), contentTypeOf(attachment.name()), Files.size(download)));
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

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not delete the temporary download {}", file, e);
    }
  }
}
