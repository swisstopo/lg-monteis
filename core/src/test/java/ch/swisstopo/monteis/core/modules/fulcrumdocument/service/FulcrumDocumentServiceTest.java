package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.util.List;
import java.util.UUID;
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
  void should_open_the_file_stored_under_the_attachment_id() {
    // given
    FulcrumDocument document =
        new FulcrumDocument(
            DOCUMENT_ID,
            null,
            "report",
            null,
            null,
            null,
            List.of(),
            new FulcrumDocumentFile(
                ATTACHMENT_ID, new DocumentMetadata("report.pdf", "application/pdf", 4)),
            null);

    // when
    service.openFileContent(document);

    // then
    then(storage).should().load(ATTACHMENT_ID);
  }

  @Test
  void should_not_find_an_unknown_document() {
    // given
    given(documentRepository.getById(DOCUMENT_ID))
        .willThrow(new ObjectNotFoundException(FulcrumDocument.class));

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.getDocument(DOCUMENT_ID));
  }
}
