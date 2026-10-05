package ch.swisstopo.monteis.core.modules.experiment.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;

class ExperimentDocumentWebMapperTest {

  private static final byte[] CONTENT = "%PDF".getBytes(StandardCharsets.UTF_8);

  private final ExperimentDocumentWebMapper mapper =
      Mappers.getMapper(ExperimentDocumentWebMapper.class);

  @Test
  void should_take_name_content_type_and_size_of_the_file() {
    // when
    DocumentMetadata metadata = mapper.toMetadata(file("report.pdf", "application/pdf"));

    // then
    assertEquals(new DocumentMetadata("report.pdf", "application/pdf", CONTENT.length), metadata);
  }

  @ParameterizedTest
  @ValueSource(strings = {"C:\\Users\\me\\report.pdf", "/home/me/report.pdf", "  report.pdf  "})
  void should_strip_the_client_path_and_whitespace_from_the_file_name(String originalFileName) {
    // when
    DocumentMetadata metadata = mapper.toMetadata(file(originalFileName, "application/pdf"));

    // then
    assertEquals("report.pdf", metadata.fileName());
  }

  @Test
  void should_reject_a_file_name_that_is_only_a_client_path() {
    // when
    ObjectBusinessValidationException exception =
        assertThrows(
            ObjectBusinessValidationException.class,
            () -> mapper.toMetadata(file("C:\\Users\\me\\", "application/pdf")));

    // then
    assertEquals("document.validation.fileName", exception.getMessageKey());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "not a mime type")
  void should_fall_back_to_octet_stream_for_an_invalid_content_type(String contentType) {
    // when
    DocumentMetadata metadata = mapper.toMetadata(file("report.pdf", contentType));

    // then
    assertEquals("application/octet-stream", metadata.contentType());
  }

  @Test
  void should_fall_back_to_octet_stream_for_a_content_type_longer_than_the_column() {
    // given
    String contentType = "text/" + "x".repeat(DocumentMetadata.MAX_LENGTH);

    // when
    DocumentMetadata metadata = mapper.toMetadata(file("report.pdf", contentType));

    // then
    assertEquals("application/octet-stream", metadata.contentType());
  }

  private static MockMultipartFile file(String originalFileName, String contentType) {
    return new MockMultipartFile("file", originalFileName, contentType, CONTENT);
  }
}
