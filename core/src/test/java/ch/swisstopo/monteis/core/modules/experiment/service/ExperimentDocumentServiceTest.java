package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentStorage;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocumentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PermissionDeniedDataAccessException;

@ExtendWith(MockitoExtension.class)
class ExperimentDocumentServiceTest {

  private static final UUID EXPERIMENT_ID = UUID.randomUUID();
  private static final DocumentMetadata METADATA =
      new DocumentMetadata("report.pdf", "application/pdf", 4);
  private static final ExperimentDocument DOCUMENT =
      new ExperimentDocument(
          UUID.randomUUID(), EXPERIMENT_ID, METADATA, OffsetDateTime.now(), "alice");

  @Mock private ExperimentRepository experimentRepository;
  @Mock private ExperimentDocumentRepository documentRepository;
  @Mock private DocumentStorage storage;
  @Mock private CurrentUserProvider currentUserProvider;

  @InjectMocks private ExperimentDocumentService service;

  @Test
  void should_record_the_upload_before_storing_its_content() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[4]);
    given(currentUserProvider.requireCurrentUsername()).willReturn("alice");
    given(documentRepository.create(EXPERIMENT_ID, METADATA, "alice")).willReturn(DOCUMENT);

    // when
    ExperimentDocument uploaded = service.upload(EXPERIMENT_ID, METADATA, content);

    // then
    assertSame(DOCUMENT, uploaded);
    InOrder order = inOrder(experimentRepository, documentRepository, storage);
    order.verify(experimentRepository).requireVisible(EXPERIMENT_ID);
    order.verify(documentRepository).create(EXPERIMENT_ID, METADATA, "alice");
    order.verify(storage).store(DOCUMENT, content);
  }

  @Test
  void should_not_upload_to_an_experiment_the_caller_cannot_see() {
    // given
    willThrow(new ObjectNotFoundException(Experiment.class))
        .given(experimentRepository)
        .requireVisible(EXPERIMENT_ID);

    // when / then
    assertThrows(
        ObjectNotFoundException.class,
        () -> service.upload(EXPERIMENT_ID, METADATA, InputStream.nullInputStream()));
    then(documentRepository).shouldHaveNoInteractions();
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_store_nothing_when_row_level_security_rejects_the_upload() {
    // given
    given(currentUserProvider.requireCurrentUsername()).willReturn("alice");
    given(documentRepository.create(EXPERIMENT_ID, METADATA, "alice"))
        .willThrow(new PermissionDeniedDataAccessException("rls", null));

    // when / then
    assertThrows(
        PermissionDeniedDataAccessException.class,
        () -> service.upload(EXPERIMENT_ID, METADATA, InputStream.nullInputStream()));
    then(storage).should(never()).store(any(), any());
  }

  @Test
  void should_list_the_documents_of_a_visible_experiment() {
    // given
    given(documentRepository.findByExperimentId(EXPERIMENT_ID)).willReturn(List.of(DOCUMENT));

    // when
    List<ExperimentDocument> documents = service.getDocuments(EXPERIMENT_ID);

    // then
    assertEquals(List.of(DOCUMENT), documents);
    then(experimentRepository).should().requireVisible(EXPERIMENT_ID);
  }

  @Test
  void should_not_list_the_documents_of_an_experiment_the_caller_cannot_see() {
    // given
    willThrow(new ObjectNotFoundException(Experiment.class))
        .given(experimentRepository)
        .requireVisible(EXPERIMENT_ID);

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.getDocuments(EXPERIMENT_ID));
    then(documentRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_hand_out_a_document_without_opening_its_content() {
    // given
    given(documentRepository.getById(EXPERIMENT_ID, DOCUMENT.id())).willReturn(DOCUMENT);

    // when
    ExperimentDocument document = service.getDocument(EXPERIMENT_ID, DOCUMENT.id());

    // then
    assertSame(DOCUMENT, document);
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_open_the_stored_content_of_a_document() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[4]);
    given(storage.load(DOCUMENT)).willReturn(content);

    // when / then
    assertSame(content, service.openContent(DOCUMENT));
  }

  @Test
  void should_not_find_an_unknown_document() {
    // given
    UUID documentId = UUID.randomUUID();
    given(documentRepository.getById(EXPERIMENT_ID, documentId))
        .willThrow(new ObjectNotFoundException(ExperimentDocument.class));

    // when / then
    assertThrows(
        ObjectNotFoundException.class, () -> service.getDocument(EXPERIMENT_ID, documentId));
  }
}
