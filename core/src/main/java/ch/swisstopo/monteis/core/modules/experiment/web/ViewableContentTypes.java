package ch.swisstopo.monteis.core.modules.experiment.web;

import java.util.List;
import org.mapstruct.Named;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * The content types the webapp may open in the browser, every other document is download only. a
 * viewed document opens from a blob url on the webapp's origin, html or svg would run its scripts
 * there with the viewer's session.
 */
final class ViewableContentTypes {

  static final String VIEWABLE = "viewable";

  private static final List<MimeType> TYPES =
      List.of(
          MimeTypeUtils.parseMimeType("application/pdf"),
          MimeTypeUtils.IMAGE_PNG,
          MimeTypeUtils.IMAGE_JPEG,
          MimeTypeUtils.IMAGE_GIF,
          MimeTypeUtils.parseMimeType("image/webp"),
          MimeTypeUtils.TEXT_PLAIN);

  private ViewableContentTypes() {}

  @Named(VIEWABLE)
  static boolean isViewable(String contentType) {
    try {
      MimeType type = MimeTypeUtils.parseMimeType(contentType);
      return TYPES.stream().anyMatch(type::equalsTypeAndSubtype);
    } catch (InvalidMimeTypeException _) {
      return false;
    }
  }
}
