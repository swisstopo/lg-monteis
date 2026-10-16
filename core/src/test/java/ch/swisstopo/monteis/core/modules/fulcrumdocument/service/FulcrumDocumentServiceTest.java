package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentServiceTest {

  private static final UUID DOCUMENT_ID = UUID.randomUUID();

  @Mock private FulcrumEntryRepository entryRepository;
  @Mock private FulcrumDocumentRepository documentRepository;
  @Mock private FulcrumDocumentStorage storage;

  @InjectMocks private FulcrumDocumentService service;

  @Test
  void should_open_the_content_stored_under_the_document_id() {
    // given
    FulcrumDocument document =
        new FulcrumDocument(
            DOCUMENT_ID,
            UUID.randomUUID(),
            new DocumentMetadata("report.pdf", "application/pdf", 4),
            OffsetDateTime.now(),
            null);

    // when
    service.openContent(document);

    // then
    then(storage).should().load(DOCUMENT_ID);
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
