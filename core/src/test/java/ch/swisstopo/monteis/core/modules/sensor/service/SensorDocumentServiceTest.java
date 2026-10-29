package ch.swisstopo.monteis.core.modules.sensor.service;

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

import ch.swisstopo.monteis.core.infrastructure.document.DocumentKey;
import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocumentRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocumentStorage;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
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
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class SensorDocumentServiceTest {

  private static final UUID SENSOR_ID = UUID.randomUUID();
  private static final UUID DOCUMENT_ID = UUID.randomUUID();
  private static final DocumentKey KEY = new DocumentKey(SENSOR_ID, DOCUMENT_ID);
  private static final DocumentMetadata METADATA =
      new DocumentMetadata("calibration.pdf", "application/pdf", 4);
  private static final SensorDocument DOCUMENT =
      new SensorDocument(DOCUMENT_ID, SENSOR_ID, METADATA, OffsetDateTime.now(), "alice");

  @Mock private SensorRepository sensorRepository;
  @Mock private SensorDocumentRepository documentRepository;
  @Mock private SensorDocumentStorage storage;
  @Mock private CurrentUserProvider currentUserProvider;

  @InjectMocks private SensorDocumentService service;

  @Test
  void should_check_the_permission_then_store_the_content_before_recording_the_upload() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[4]);
    given(currentUserProvider.requireCurrentUsername()).willReturn("alice");
    given(sensorRepository.canWriteAnExperimentOf(SENSOR_ID)).willReturn(true);
    given(documentRepository.nextId()).willReturn(DOCUMENT_ID);
    given(documentRepository.create(DOCUMENT_ID, SENSOR_ID, METADATA, "alice"))
        .willReturn(DOCUMENT);

    // when
    SensorDocument uploaded = service.upload(SENSOR_ID, METADATA, content);

    // then
    assertSame(DOCUMENT, uploaded);
    InOrder order = inOrder(sensorRepository, documentRepository, storage);
    order.verify(sensorRepository).requireVisible(SENSOR_ID);
    order.verify(sensorRepository).canWriteAnExperimentOf(SENSOR_ID);
    order.verify(documentRepository).nextId();
    order.verify(storage).store(KEY, METADATA, content);
    order.verify(documentRepository).create(DOCUMENT_ID, SENSOR_ID, METADATA, "alice");
    then(storage).should(never()).delete(any());
  }

  @Test
  void should_not_upload_to_a_sensor_the_caller_cannot_see() {
    // given
    willThrow(new ObjectNotFoundException(Sensor.class))
        .given(sensorRepository)
        .requireVisible(SENSOR_ID);

    // when / then
    assertThrows(
        ObjectNotFoundException.class,
        () -> service.upload(SENSOR_ID, METADATA, InputStream.nullInputStream()));
    then(documentRepository).shouldHaveNoInteractions();
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_an_upload_the_caller_may_not_make_before_storing_anything() {
    // given
    given(sensorRepository.canWriteAnExperimentOf(SENSOR_ID)).willReturn(false);

    // when / then
    assertThrows(
        AccessDeniedException.class,
        () -> service.upload(SENSOR_ID, METADATA, InputStream.nullInputStream()));
    then(documentRepository).shouldHaveNoInteractions();
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_delete_the_stored_content_when_row_level_security_rejects_the_upload() {
    // given
    PermissionDeniedDataAccessException rejection =
        new PermissionDeniedDataAccessException("rls", null);
    IllegalStateException deleteFailure = new IllegalStateException("S3 unavailable");
    given(currentUserProvider.requireCurrentUsername()).willReturn("alice");
    given(sensorRepository.canWriteAnExperimentOf(SENSOR_ID)).willReturn(true);
    given(documentRepository.nextId()).willReturn(DOCUMENT_ID);
    given(documentRepository.create(DOCUMENT_ID, SENSOR_ID, METADATA, "alice"))
        .willThrow(rejection);
    willThrow(deleteFailure).given(storage).delete(KEY);

    // when
    PermissionDeniedDataAccessException thrown =
        assertThrows(
            PermissionDeniedDataAccessException.class,
            () -> service.upload(SENSOR_ID, METADATA, InputStream.nullInputStream()));

    // then
    assertSame(rejection, thrown);
    then(storage).should().delete(KEY);
    assertThat(thrown.getSuppressed()).containsExactly(deleteFailure);
  }

  @Test
  void should_list_the_documents_of_a_visible_sensor() {
    // given
    given(documentRepository.findBySensorId(SENSOR_ID)).willReturn(List.of(DOCUMENT));

    // when
    List<SensorDocument> documents = service.getDocuments(SENSOR_ID);

    // then
    assertEquals(List.of(DOCUMENT), documents);
    then(sensorRepository).should().requireVisible(SENSOR_ID);
  }

  @Test
  void should_not_list_the_documents_of_a_sensor_the_caller_cannot_see() {
    // given
    willThrow(new ObjectNotFoundException(Sensor.class))
        .given(sensorRepository)
        .requireVisible(SENSOR_ID);

    // when / then
    assertThrows(ObjectNotFoundException.class, () -> service.getDocuments(SENSOR_ID));
    then(documentRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_open_the_stored_content_of_a_document() {
    // given
    InputStream content = new ByteArrayInputStream(new byte[4]);
    given(storage.load(KEY)).willReturn(content);

    // when / then
    assertSame(content, service.openContent(DOCUMENT));
  }
}
