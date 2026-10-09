package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.util.Map;

public record DocumentMetadata(String fileName, String contentType, long sizeBytes) {

  // column length of experiment_documents.file_name and .content_type
  public static final int MAX_LENGTH = 255;

  public DocumentMetadata {
    if (sizeBytes <= 0) {
      throw new ObjectBusinessValidationException("document.validation.empty", Map.of());
    }
    if (fileName == null || fileName.isBlank() || fileName.length() > MAX_LENGTH) {
      throw new ObjectBusinessValidationException(
          "document.validation.fileName", Map.of("max", MAX_LENGTH));
    }
    // a bug, not a validation message: the web mapper replaces any unusable content type with
    // application/octet-stream
    if (contentType == null || contentType.isBlank() || contentType.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Invalid content type: " + contentType);
    }
  }

  public static DocumentMetadata of(String fileName, String contentType, long sizeBytes) {
    return new DocumentMetadata(fileName, contentType, sizeBytes);
  }
}
