package ch.swisstopo.monteis.core.modules.fulcrumdocument.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FulcrumDocumentFileTest {

  private static final UUID ATTACHMENT_ID = UUID.randomUUID();

  @ParameterizedTest
  @CsvSource({
    "report.pdf, application/pdf",
    "drilling.png, image/png",
    "notes.unknownext, application/octet-stream",
    "map.svg, application/octet-stream"
  })
  void should_take_the_content_type_from_the_name_unless_the_browser_would_run_it(
      String fulcrumName, String contentType) {
    // when
    FulcrumDocumentFile file = FulcrumDocumentFile.of(ATTACHMENT_ID, fulcrumName, 8);

    // then
    assertThat(file.metadata()).isEqualTo(new DocumentMetadata(fulcrumName, contentType, 8));
  }

  @Test
  void should_cut_a_long_file_name_but_keep_its_extension() {
    // when
    FulcrumDocumentFile file = FulcrumDocumentFile.of(ATTACHMENT_ID, "a".repeat(300) + ".pdf", 8);

    // then
    assertThat(file.metadata().fileName())
        .hasSize(DocumentMetadata.MAX_LENGTH)
        .startsWith("aaa")
        .endsWith(".pdf");
    assertThat(file.metadata().contentType()).isEqualTo("application/pdf");
  }

  @Test
  void should_keep_the_attachment_id_and_size() {
    // when
    FulcrumDocumentFile file = FulcrumDocumentFile.of(ATTACHMENT_ID, "report.pdf", 1234);

    // then
    assertThat(file.attachmentId()).isEqualTo(ATTACHMENT_ID);
    assertThat(file.metadata().sizeBytes()).isEqualTo(1234);
  }
}
