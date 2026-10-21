package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

// the one file of a Fulcrum Document, its content lives in FulcrumDocumentStorage under Fulcrum's
// attachment id
public record FulcrumDocumentFile(UUID attachmentId, DocumentMetadata metadata) {

  // a longer tail after the last dot is part of the name, not an extension
  private static final int MAX_EXTENSION_LENGTH = 10;

  public static FulcrumDocumentFile of(UUID attachmentId, String fulcrumName, long sizeBytes) {
    return new FulcrumDocumentFile(
        attachmentId,
        DocumentMetadata.of(fileNameOf(fulcrumName), contentTypeOf(fulcrumName), sizeBytes));
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
