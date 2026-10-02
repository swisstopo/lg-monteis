package ch.swisstopo.monteis.core.modules.experiment.service;

import static org.junit.jupiter.api.Assertions.assertAll;
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
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentStorage;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentUpload;
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
  private static final DocumentUpload UPLOAD =
      new DocumentUpload("report.pdf", "application/pdf", 4);
  private static final ExperimentDocument DOCUMENT =
      new ExperimentDocument(
          UUID.randomUUID(),
          EXPERIMENT_ID,
          "report.pdf",
          "application/pdf",
          4,
          OffsetDateTime.now(),
          "alice");

  @Mock private ExperimentRepository experimentRepository;
  @Mock private ExperimentDocumentRepository documentRepository;
  @Mock private DocumentStorage storage;

  @InjectMocks private ExperimentDocumentService service;

  @Test
  void should_record_the_upload_before_storing_its_content() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[4]);
    given(documentRepository.create(EXPERIMENT_ID, UPLOAD)).willReturn(DOCUMENT);

    // when
    ExperimentDocument uploaded = service.upload(EXPERIMENT_ID, UPLOAD, content);

    // then
    assertSame(DOCUMENT, uploaded);
    InOrder order = inOrder(experimentRepository, documentRepository, storage);
    order.verify(experimentRepository).getById(EXPERIMENT_ID);
    order.verify(documentRepository).create(EXPERIMENT_ID, UPLOAD);
    order.verify(storage).store(DOCUMENT, content);
  }

  @Test
  void should_not_upload_to_an_experiment_the_caller_cannot_see() {
    // given
    given(experimentRepository.getById(EXPERIMENT_ID))
        .willThrow(new ObjectNotFoundException(Experiment.class));

    // when / then
    assertThrows(
        ObjectNotFoundException.class,
        () -> service.upload(EXPERIMENT_ID, UPLOAD, InputStream.nullInputStream()));
    then(documentRepository).shouldHaveNoInteractions();
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_store_nothing_when_row_level_security_rejects_the_upload() {
    // given
    given(documentRepository.create(EXPERIMENT_ID, UPLOAD))
        .willThrow(new PermissionDeniedDataAccessException("rls", null));

    // when / then
    assertThrows(
        PermissionDeniedDataAccessException.class,
        () -> service.upload(EXPERIMENT_ID, UPLOAD, InputStream.nullInputStream()));
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
    then(experimentRepository).should().getById(EXPERIMENT_ID);
  }

  @Test
  void should_not_list_the_documents_of_an_experiment_the_caller_cannot_see() {
    // given
    willThrow(new ObjectNotFoundException(Experiment.class))
        .given(experimentRepository)
        .getById(EXPERIMENT_ID);

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.getDocuments(EXPERIMENT_ID));
    then(documentRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_hand_out_the_document_with_its_stored_content() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[4]);
    given(documentRepository.getById(EXPERIMENT_ID, DOCUMENT.id())).willReturn(DOCUMENT);
    given(storage.load(DOCUMENT)).willReturn(content);

    // when
    DocumentDownload download = service.download(EXPERIMENT_ID, DOCUMENT.id());

    // then
    assertAll(
        () -> assertSame(DOCUMENT, download.document()),
        () -> assertSame(content, download.content()));
  }

  @Test
  void should_not_open_the_storage_for_an_unknown_document() {
    // given
    UUID documentId = UUID.randomUUID();
    given(documentRepository.getById(EXPERIMENT_ID, documentId))
        .willThrow(new ObjectNotFoundException(ExperimentDocument.class));

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.download(EXPERIMENT_ID, documentId));
    then(storage).shouldHaveNoInteractions();
  }
}
