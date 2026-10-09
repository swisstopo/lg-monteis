package ch.swisstopo.monteis.core.modules.experiment.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ViewableContentTypesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/pdf",
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "text/plain",
        "Text/Plain; charset=utf-8",
        "APPLICATION/PDF"
      })
  void should_let_a_type_the_browser_renders_without_scripts_be_viewed(String contentType) {
    assertTrue(ViewableContentTypes.isViewable(contentType));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text/html",
        "text/html; charset=utf-8",
        "image/svg+xml",
        "application/xhtml+xml",
        "text/xml",
        "application/xml",
        "application/octet-stream",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      })
  void should_only_let_a_type_that_can_run_scripts_be_downloaded(String contentType) {
    assertFalse(ViewableContentTypes.isViewable(contentType));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "not a mime type")
  void should_not_let_an_unreadable_type_be_viewed(String contentType) {
    assertFalse(ViewableContentTypes.isViewable(contentType));
  }
}
