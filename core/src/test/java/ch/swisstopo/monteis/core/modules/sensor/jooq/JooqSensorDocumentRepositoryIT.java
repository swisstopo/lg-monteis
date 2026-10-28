package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_DOCUMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocument;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.transaction.annotation.Transactional;

@IT
class JooqSensorDocumentRepositoryIT {

  private static final DocumentMetadata REPORT =
      new DocumentMetadata("calibration.pdf", "application/pdf", 42);

  @Autowired private JooqSensorDocumentRepository repository;
  @Autowired private DSLContext dsl;

  @Test
  @Transactional
  void should_create_a_document_under_the_given_id_uploaded_now_by_the_given_user() {
    // given
    UUID sensorId = sensorOf(SeedData.EXPERIMENT_ALPHA);
    UUID documentId = callAsAdmin(repository::nextId);

    // when
    SensorDocument document =
        callAsAdmin(
            () ->
                repository.create(
                    documentId, sensorId, REPORT, SecurityContextTestSupport.USERNAME));

    // then
    assertThat(document.id()).isEqualTo(documentId);
    assertThat(document.sensorId()).isEqualTo(sensorId);
    assertThat(document.metadata()).isEqualTo(REPORT);
    assertThat(document.uploadedAt())
        .isCloseTo(OffsetDateTime.now(), within(1, ChronoUnit.MINUTES));
    assertThat(document.uploadedBy()).isEqualTo(SecurityContextTestSupport.USERNAME);
    assertThat(callAsAdmin(() -> repository.getById(sensorId, document.id()))).isEqualTo(document);
  }

  @Test
  @Transactional
  void should_list_the_documents_of_a_sensor_newest_first() {
    // given: the older one is inserted last, so it has the higher id, the order must come from
    // the upload time (both would share now() of the test transaction otherwise)
    UUID sensorId = sensorOf(SeedData.EXPERIMENT_ALPHA);
    SensorDocument newer = createAsAdmin(sensorId);
    UUID older = insertDocumentUploadedAt(sensorId, OffsetDateTime.now().minusDays(1));
    createAsAdmin(sensorOf(SeedData.EXPERIMENT_BETA));

    // when
    List<SensorDocument> documents = callAsAdmin(() -> repository.findBySensorId(sensorId));

    // then
    assertThat(documents).extracting(SensorDocument::id).containsExactly(newer.id(), older);
  }

  @Test
  @Transactional
  void should_not_find_a_document_under_another_sensor() {
    // given
    SensorDocument document = createAsAdmin(sensorOf(SeedData.EXPERIMENT_ALPHA));
    UUID otherSensorId = sensorOf(SeedData.EXPERIMENT_BETA);

    // when / then
    assertThrows(
        ObjectNotFoundException.class,
        () -> callAsAdmin(() -> repository.getById(otherSensorId, document.id())));
  }

  @Test
  @Transactional
  void should_hide_the_documents_of_a_sensor_the_user_may_not_read() {
    // given
    UUID readable = sensorOf(SeedData.EXPERIMENT_ALPHA);
    UUID hidden = sensorOf(SeedData.EXPERIMENT_BETA);
    SensorDocument readableDocument = createAsAdmin(readable);
    UUID hiddenDocumentId = createAsAdmin(hidden).id();

    // when / then
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA),
        () -> {
          assertThat(repository.findBySensorId(readable))
              .extracting(SensorDocument::id)
              .containsExactly(readableDocument.id());
          assertThat(repository.findBySensorId(hidden)).isEmpty();
          assertThrows(
              ObjectNotFoundException.class, () -> repository.getById(hidden, hiddenDocumentId));
        });
  }

  @Test
  @Transactional
  void should_let_a_writer_of_one_of_the_sensors_experiments_add_documents() {
    // given
    UUID sensorId = sensorOf(SeedData.EXPERIMENT_ALPHA);

    // when / then
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA),
        List.of(SeedData.EXPERIMENT_ALPHA),
        () ->
            assertThat(
                    repository
                        .create(
                            repository.nextId(),
                            sensorId,
                            REPORT,
                            SecurityContextTestSupport.USERNAME)
                        .id())
                .isNotNull());
  }

  @Test
  @Transactional
  void should_reject_an_upload_by_a_writer_of_another_experiment() {
    // given
    UUID sensorId = sensorOf(SeedData.EXPERIMENT_ALPHA);

    // when / then: the WITH CHECK of sensor_documents_insert aborts the transaction, so this is
    // the last statement of the test
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA, SeedData.EXPERIMENT_BETA),
        List.of(SeedData.EXPERIMENT_BETA),
        () -> {
          UUID documentId = repository.nextId();
          assertThrows(
              PermissionDeniedDataAccessException.class,
              () ->
                  repository.create(
                      documentId, sensorId, REPORT, SecurityContextTestSupport.USERNAME));
        });
  }

  @Test
  @Transactional
  void should_reject_updating_and_deleting_a_document_even_for_an_admin() {
    // given
    SensorDocument document = createAsAdmin(sensorOf(SeedData.EXPERIMENT_ALPHA));

    // when: no UPDATE or DELETE policy, RLS filters every row out
    int updated =
        callAsAdmin(
            () ->
                dsl.update(SENSOR_DOCUMENTS)
                    .set(SENSOR_DOCUMENTS.FILE_NAME, "renamed.pdf")
                    .where(SENSOR_DOCUMENTS.ID.eq(document.id()))
                    .execute());
    int deleted =
        callAsAdmin(
            () ->
                dsl.deleteFrom(SENSOR_DOCUMENTS)
                    .where(SENSOR_DOCUMENTS.ID.eq(document.id()))
                    .execute());

    // then
    assertThat(updated).isZero();
    assertThat(deleted).isZero();
  }

  private SensorDocument createAsAdmin(UUID sensorId) {
    return callAsAdmin(
        () ->
            repository.create(
                repository.nextId(), sensorId, REPORT, SecurityContextTestSupport.USERNAME));
  }

  // the seeded experiments share no sensors
  private UUID sensorOf(UUID experimentId) {
    return callAsAdmin(
        () ->
            dsl.select(EXPERIMENT_SENSOR.SENSOR_ID)
                .from(EXPERIMENT_SENSOR)
                .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(experimentId))
                .limit(1)
                .fetchSingle()
                .value1());
  }

  private UUID insertDocumentUploadedAt(UUID sensorId, OffsetDateTime uploadedAt) {
    return callAsAdmin(
        () ->
            dsl.insertInto(SENSOR_DOCUMENTS)
                .set(SENSOR_DOCUMENTS.SENSOR_ID, sensorId)
                .set(SENSOR_DOCUMENTS.FILE_NAME, "older.pdf")
                .set(SENSOR_DOCUMENTS.CONTENT_TYPE, "application/pdf")
                .set(SENSOR_DOCUMENTS.SIZE_BYTES, 1L)
                .set(SENSOR_DOCUMENTS.UPLOADED_AT, uploadedAt)
                .set(SENSOR_DOCUMENTS.UPLOADED_BY, SecurityContextTestSupport.USERNAME)
                .returning(SENSOR_DOCUMENTS.ID)
                .fetchSingle()
                .getId());
  }
}
