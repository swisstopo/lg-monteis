package ch.swisstopo.monteis.core.modules.experiment.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
