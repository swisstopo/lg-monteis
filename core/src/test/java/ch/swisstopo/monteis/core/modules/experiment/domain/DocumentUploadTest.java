package ch.swisstopo.monteis.core.modules.experiment.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentUploadTest {

  @Test
  void should_keep_file_name_and_content_type_of_a_regular_upload() {
    // when
    DocumentUpload upload = DocumentUpload.of("report.pdf", "application/pdf", 42);

    // then
    assertAll(
        () -> assertEquals("report.pdf", upload.fileName()),
        () -> assertEquals("application/pdf", upload.contentType()),
        () -> assertEquals(42, upload.sizeBytes()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"C:\\Users\\me\\report.pdf", "/home/me/report.pdf", "  report.pdf  "})
  void should_strip_the_client_path_and_whitespace_from_the_file_name(String originalFileName) {
    // when
    DocumentUpload upload = DocumentUpload.of(originalFileName, "application/pdf", 42);

    // then
    assertEquals("report.pdf", upload.fileName());
  }

  @Test
  void should_reject_an_empty_file() {
    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> DocumentUpload.of("report.pdf", "application/pdf", 0));

    // then
    assertEquals("document.validation.empty", exception.getMessageKey());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "C:\\Users\\me\\"})
  void should_reject_a_missing_file_name(String originalFileName) {
    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> DocumentUpload.of(originalFileName, "application/pdf", 42));

    // then
    assertEquals("document.validation.fileName", exception.getMessageKey());
  }

  @Test
  void should_reject_a_file_name_longer_than_the_column() {
    // given
    String fileName = "a".repeat(DocumentUpload.MAX_LENGTH + 1);

    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> DocumentUpload.of(fileName, "application/pdf", 42));

    // then
    assertEquals("document.validation.fileName", exception.getMessageKey());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "not a mime type")
  void should_fall_back_to_octet_stream_for_an_invalid_content_type(String contentType) {
    // when
    DocumentUpload upload = DocumentUpload.of("report.pdf", contentType, 42);

    // then
    assertEquals("application/octet-stream", upload.contentType());
  }

  @Test
  void should_fall_back_to_octet_stream_for_a_content_type_longer_than_the_column() {
    // given
    String contentType = "text/" + "x".repeat(DocumentUpload.MAX_LENGTH);

    // when
    DocumentUpload upload = DocumentUpload.of("report.pdf", contentType, 42);

    // then
    assertEquals("application/octet-stream", upload.contentType());
  }
}
