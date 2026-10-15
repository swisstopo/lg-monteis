package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentServiceTest {

  private static final UUID FILE_ID = UUID.randomUUID();

  @Mock private FulcrumDocumentRepository repository;
  @Mock private FulcrumDocumentStorage storage;

  @InjectMocks private FulcrumDocumentService service;

  @Test
  void should_return_a_stored_file() {
    // given
    FulcrumDocumentFile file = file(OffsetDateTime.now());
    given(repository.getFileById(FILE_ID)).willReturn(file);

    // when / then
    assertThat(service.getStoredFile(FILE_ID)).isSameAs(file);
  }

  @Test
  void should_not_find_a_file_whose_content_is_not_stored_yet() {
    // given
    given(repository.getFileById(FILE_ID)).willReturn(file(null));

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.getStoredFile(FILE_ID));
  }

  private static FulcrumDocumentFile file(OffsetDateTime storedAt) {
    return new FulcrumDocumentFile(
        FILE_ID,
        UUID.randomUUID(),
        "report.pdf",
        "application/pdf",
        storedAt == null ? null : 4L,
        storedAt,
        null);
  }
}
