package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

// imports the file of one Fulcrum Document into MONTEIS: download -> temp file -> storage -> row.
// storage first and the row after, like an experiment document upload: a failure in between leaves
// an object nobody sees, the next sync overwrites it under the same key
@Service
public class FulcrumDocumentImportService {

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentImportService.class);

  private final FulcrumAttachmentDownloadClient downloadClient;
  private final FulcrumDocumentStorage storage;
  private final FulcrumDocumentRepository documentRepository;
  private final FulcrumLiteratureMapper literatureMapper;

  public FulcrumDocumentImportService(
      FulcrumAttachmentDownloadClient downloadClient,
      FulcrumDocumentStorage storage,
      FulcrumDocumentRepository documentRepository,
      FulcrumLiteratureMapper literatureMapper) {
    this.downloadClient = downloadClient;
    this.storage = storage;
    this.documentRepository = documentRepository;
    this.literatureMapper = literatureMapper;
  }

  // one failed import must not stop the others, the next sync tries it again
  public boolean importQuietly(
      FulcrumLiteratureEntry entry, FulcrumAttachmentReference attachment, OffsetDateTime seenAt) {
    Path download = null;
    try {
      download = downloadToTempFile(attachment);
      FulcrumDocumentFile file = fileOf(attachment, download);
      storage.store(file.attachmentId(), file.metadata().contentType(), download);
      documentRepository.saveSeen(List.of(literatureMapper.toDocument(entry, file)), seenAt);
      return true;
    } catch (IOException | RuntimeException e) {
      log.warn(
          "Could not import file {} of Fulcrum Document {}", attachment.id(), entry.recordId(), e);
      return false;
    } finally {
      deleteQuietly(download);
    }
  }

  private Path downloadToTempFile(FulcrumAttachmentReference attachment) throws IOException {
    Path download = Files.createTempFile("fulcrum-document-", ".download");
    try (InputStream content = downloadClient.download(attachment.id())) {
      Files.copy(content, download, StandardCopyOption.REPLACE_EXISTING);
    }
    return download;
  }

  private static FulcrumDocumentFile fileOf(FulcrumAttachmentReference attachment, Path download)
      throws IOException {
    return new FulcrumDocumentFile(
        attachment.id(),
        DocumentMetadata.of(
            fileNameOf(attachment.name()), contentTypeOf(attachment.name()), Files.size(download)));
  }

  private static String fileNameOf(String name) {
    return name.length() <= DocumentMetadata.MAX_LENGTH
        ? name
        : name.substring(0, DocumentMetadata.MAX_LENGTH);
  }

  // Fulcrum serves every file as application/octet-stream, the browser could not show a pdf
  private static String contentTypeOf(String fileName) {
    return MediaTypeFactory.getMediaType(fileName)
        .map(MediaType::toString)
        .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }

  private static void deleteQuietly(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not delete the temporary download {}", file, e);
    }
  }
}
