package ch.swisstopo.monteis.core.modules.experiment.domain;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Name, content type and size of a document's file, without its content. The constructor holds the
 * rules every document has, so none can be built without them.
 */
public record DocumentMetadata(String fileName, String contentType, long sizeBytes) {

  // column length of experiment_documents.file_name and .content_type
  public static final int MAX_LENGTH = 255;

  // a viewed document opens from a blob url on the webapp's origin. html or svg would run its
  // scripts there with the viewer's session, so only types a browser renders without scripts open
  private static final Set<String> VIEWABLE_TYPES =
      Set.of("application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp", "text/plain");

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

  /** Whether the webapp may open the document in the browser instead of downloading it. */
  public boolean isViewable() {
    String mediaType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    return VIEWABLE_TYPES.contains(mediaType);
  }

  public static DocumentMetadata of(String fileName, String contentType, long sizeBytes) {
    return new DocumentMetadata(fileName, contentType, sizeBytes);
  }
}
