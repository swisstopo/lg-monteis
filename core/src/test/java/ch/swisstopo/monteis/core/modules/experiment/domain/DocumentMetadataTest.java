package ch.swisstopo.monteis.core.modules.experiment.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentMetadataTest {

  @Test
  void should_keep_name_content_type_and_size() {
    // when
    DocumentMetadata metadata = DocumentMetadata.of("report.pdf", "application/pdf", 42);

    // then
    assertAll(
        () -> assertEquals("report.pdf", metadata.fileName()),
        () -> assertEquals("application/pdf", metadata.contentType()),
        () -> assertEquals(42, metadata.sizeBytes()));
  }

  @Test
  void should_reject_an_empty_file() {
    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> new DocumentMetadata("report.pdf", "application/pdf", 0));

    // then
    assertEquals("document.validation.empty", exception.getMessageKey());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void should_reject_a_missing_file_name(String fileName) {
    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> new DocumentMetadata(fileName, "application/pdf", 42));

    // then
    assertEquals("document.validation.fileName", exception.getMessageKey());
  }

  @Test
  void should_reject_a_file_name_longer_than_the_column() {
    // given
    String fileName = "a".repeat(DocumentMetadata.MAX_LENGTH + 1);

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> new DocumentMetadata(fileName, "application/pdf", 42));

    // then
    assertEquals("document.validation.fileName", exception.getMessageKey());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void should_reject_a_missing_content_type(String contentType) {
    assertThrows(
        IllegalArgumentException.class, () -> new DocumentMetadata("report.pdf", contentType, 42));
  }

  @Test
  void should_reject_a_content_type_longer_than_the_column() {
    // given
    String contentType = "text/" + "x".repeat(DocumentMetadata.MAX_LENGTH);

    // when / then
    assertThrows(
        IllegalArgumentException.class, () -> new DocumentMetadata("report.pdf", contentType, 42));
  }

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
    assertTrue(new DocumentMetadata("report", contentType, 42).isViewable());
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
    assertFalse(new DocumentMetadata("report", contentType, 42).isViewable());
  }
}
