package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import ch.swisstopo.monteis.core.infrastructure.api.ViewableContentTypes;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

public record FulcrumDocumentFile(UUID attachmentId, DocumentMetadata metadata) {

  // longer tail after last dot = part of name
  private static final int MAX_EXTENSION_LENGTH = 10;

  public static FulcrumDocumentFile of(UUID attachmentId, String fulcrumName, long sizeBytes) {
    return new FulcrumDocumentFile(
        attachmentId,
        DocumentMetadata.of(fileNameOf(fulcrumName), contentTypeOf(fulcrumName), sizeBytes));
  }

  // keep extension, content type and user's os go by it
  private static String fileNameOf(String name) {
    if (name.length() <= DocumentMetadata.MAX_LENGTH) {
      return name;
    }
    int dot = name.lastIndexOf('.');
    String extension =
        dot > 0 && name.length() - dot <= MAX_EXTENSION_LENGTH ? name.substring(dot) : "";
    return name.substring(0, DocumentMetadata.MAX_LENGTH - extension.length()) + extension;
  }

  // Fulcrum serves octet-stream, browser couldn't show a pdf. only viewable types, html/svg would
  // be active content on the api
  private static String contentTypeOf(String fileName) {
    return MediaTypeFactory.getMediaType(fileName)
        .map(MediaType::toString)
        .filter(ViewableContentTypes::isViewable)
        .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }
}
