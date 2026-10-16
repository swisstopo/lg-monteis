package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumCitation;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

// copies one Fulcrum attachment into MONTEIS: download -> temp file -> storage -> entry row.
// storage first and the row after, like an experiment document upload: a failure in between leaves
// an object nobody sees, the next sync overwrites it under the same key
@Service
public class FulcrumDocumentCopier {

  private static final Logger log = LoggerFactory.getLogger(FulcrumDocumentCopier.class);

  private final FulcrumAttachmentDownloadClient downloadClient;
  private final FulcrumDocumentStorage storage;
  private final FulcrumEntryRepository entryRepository;

  public FulcrumDocumentCopier(
      FulcrumAttachmentDownloadClient downloadClient,
      FulcrumDocumentStorage storage,
      FulcrumEntryRepository entryRepository) {
    this.downloadClient = downloadClient;
    this.storage = storage;
    this.entryRepository = entryRepository;
  }

  // one failed copy must not stop the others, the next sync tries it again
  public boolean copyQuietly(
      FulcrumCitation citation, FulcrumAttachmentReference attachment, OffsetDateTime seenAt) {
    Path download = null;
    try {
      download = downloadToTempFile(attachment);
      FulcrumDocument document = documentOf(attachment, download);
      storage.store(document.id(), document.metadata().contentType(), download);
      entryRepository.saveWithDocument(citation, document, seenAt);
      return true;
    } catch (IOException | RuntimeException e) {
      log.warn("Could not copy file {} of Fulcrum entry {}", attachment.id(), citation.id(), e);
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

  private static FulcrumDocument documentOf(FulcrumAttachmentReference attachment, Path download)
      throws IOException {
    return new FulcrumDocument(
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
