package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.assertj.core.api.Assertions.assertThat;
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
  private static final UUID DOCUMENT_ID = UUID.randomUUID();
  private static final DocumentMetadata METADATA =
      new DocumentMetadata("report.pdf", "application/pdf", 4);
  private static final ExperimentDocument DOCUMENT =
      new ExperimentDocument(DOCUMENT_ID, EXPERIMENT_ID, METADATA, OffsetDateTime.now(), "alice");

  @Mock private ExperimentRepository experimentRepository;
  @Mock private ExperimentDocumentRepository documentRepository;
  @Mock private DocumentStorage storage;
  @Mock private CurrentUserProvider currentUserProvider;

  @InjectMocks private ExperimentDocumentService service;

  @Test
  void should_store_the_content_before_recording_the_upload() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[4]);
    given(currentUserProvider.requireCurrentUsername()).willReturn("alice");
    given(documentRepository.nextId()).willReturn(DOCUMENT_ID);
    given(documentRepository.create(DOCUMENT_ID, EXPERIMENT_ID, METADATA, "alice"))
        .willReturn(DOCUMENT);

    // when
    ExperimentDocument uploaded = service.upload(EXPERIMENT_ID, METADATA, content);

    // then
    assertSame(DOCUMENT, uploaded);
    InOrder order = inOrder(experimentRepository, documentRepository, storage);
    order.verify(experimentRepository).requireVisible(EXPERIMENT_ID);
    order.verify(documentRepository).nextId();
    order.verify(storage).store(EXPERIMENT_ID, DOCUMENT_ID, METADATA, content);
    order.verify(documentRepository).create(DOCUMENT_ID, EXPERIMENT_ID, METADATA, "alice");
    then(storage).should(never()).delete(any(), any());
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
  void should_record_nothing_when_storing_the_content_fails() {
    // given
    given(documentRepository.nextId()).willReturn(DOCUMENT_ID);
    willThrow(new IllegalStateException("S3 unavailable"))
        .given(storage)
        .store(any(), any(), any(), any());

    // when / then
    assertThrows(
        IllegalStateException.class,
        () -> service.upload(EXPERIMENT_ID, METADATA, InputStream.nullInputStream()));
    then(documentRepository).should(never()).create(any(), any(), any(), any());
    then(storage).should(never()).delete(any(), any());
  }

  @Test
  void should_delete_the_stored_content_when_row_level_security_rejects_the_upload() {
    // given
    PermissionDeniedDataAccessException rejection =
        new PermissionDeniedDataAccessException("rls", null);
    given(currentUserProvider.requireCurrentUsername()).willReturn("alice");
    given(documentRepository.nextId()).willReturn(DOCUMENT_ID);
    given(documentRepository.create(DOCUMENT_ID, EXPERIMENT_ID, METADATA, "alice"))
        .willThrow(rejection);

    // when
    PermissionDeniedDataAccessException thrown =
        assertThrows(
            PermissionDeniedDataAccessException.class,
            () -> service.upload(EXPERIMENT_ID, METADATA, InputStream.nullInputStream()));

    // then
    assertSame(rejection, thrown);
    then(storage).should().delete(EXPERIMENT_ID, DOCUMENT_ID);
  }

  @Test
  void should_keep_the_insert_failure_when_deleting_the_stored_content_fails_too() {
    // given
    PermissionDeniedDataAccessException rejection =
        new PermissionDeniedDataAccessException("rls", null);
    IllegalStateException deleteFailure = new IllegalStateException("S3 unavailable");
    given(currentUserProvider.requireCurrentUsername()).willReturn("alice");
    given(documentRepository.nextId()).willReturn(DOCUMENT_ID);
    given(documentRepository.create(DOCUMENT_ID, EXPERIMENT_ID, METADATA, "alice"))
        .willThrow(rejection);
    willThrow(deleteFailure).given(storage).delete(EXPERIMENT_ID, DOCUMENT_ID);

    // when
    PermissionDeniedDataAccessException thrown =
        assertThrows(
            PermissionDeniedDataAccessException.class,
            () -> service.upload(EXPERIMENT_ID, METADATA, InputStream.nullInputStream()));

    // then
    assertSame(rejection, thrown);
    assertThat(thrown.getSuppressed()).containsExactly(deleteFailure);
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
