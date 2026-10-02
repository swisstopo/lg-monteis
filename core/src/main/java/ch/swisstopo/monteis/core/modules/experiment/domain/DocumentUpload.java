package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.util.Map;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeTypeUtils;

/** A file about to be stored as an {@link ExperimentDocument}. */
public record DocumentUpload(String fileName, String contentType, long sizeBytes) {

  // column length of experiment_documents.file_name and .content_type
  static final int MAX_LENGTH = 255;

  /** Validates an upload as the browser sent it and normalizes its file name and content type. */
  public static DocumentUpload of(String originalFileName, String contentType, long sizeBytes) {
    if (sizeBytes == 0) {
      throw new ObjectBusinessValidationException("document.validation.empty", Map.of());
    }
    return new DocumentUpload(fileNameOf(originalFileName), contentTypeOf(contentType), sizeBytes);
  }

  private static String fileNameOf(String originalFileName) {
    String fileName = lastPathSegment(originalFileName == null ? "" : originalFileName).strip();
    if (fileName.isEmpty() || fileName.length() > MAX_LENGTH) {
      throw new ObjectBusinessValidationException(
          "document.validation.fileName", Map.of("max", MAX_LENGTH));
    }
    return fileName;
  }

  // some browsers send the full client path
  private static String lastPathSegment(String path) {
    return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
  }

  private static String contentTypeOf(String contentType) {
    try {
      String normalized = MimeTypeUtils.parseMimeType(contentType).toString();
      return normalized.length() <= MAX_LENGTH
          ? normalized
          : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
    } catch (InvalidMimeTypeException _) {
      return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
    }
  }
}
