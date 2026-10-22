package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentServiceTest {

  private static final UUID DOCUMENT_ID = UUID.randomUUID();
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();

  @Mock private FulcrumDocumentRepository documentRepository;
  @Mock private FulcrumDocumentStorage storage;

  @InjectMocks private FulcrumDocumentService service;

  @Test
  void should_get_the_documents_from_the_repository() {
    // given
    List<FulcrumDocument> documents = List.of(document(null));
    given(documentRepository.findAll()).willReturn(documents);

    // when / then
    assertThat(service.getDocuments()).isEqualTo(documents);
  }

  @Test
  void should_open_the_file_stored_under_the_attachment_id() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
    given(storage.load(ATTACHMENT_ID)).willReturn(content);

    // when / then
    assertThat(service.openFileContent(document(null))).isSameAs(content);
  }

  // backup is why removed documents are kept (PO)
  @Test
  void should_still_open_the_file_of_a_document_fulcrum_no_longer_has() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
    given(storage.load(ATTACHMENT_ID)).willReturn(content);

    // when / then
    assertThat(
            service.openFileContent(
                document(OffsetDateTime.of(2026, 10, 1, 2, 0, 0, 0, ZoneOffset.UTC))))
        .isSameAs(content);
  }

  private static FulcrumDocument document(@Nullable OffsetDateTime removedAt) {
    return new FulcrumDocument(
        DOCUMENT_ID,
        null,
        "report",
        null,
        null,
        null,
        List.of(),
        new FulcrumDocumentFile(
            ATTACHMENT_ID, new DocumentMetadata("report.pdf", "application/pdf", 4)),
        removedAt);
  }
}
