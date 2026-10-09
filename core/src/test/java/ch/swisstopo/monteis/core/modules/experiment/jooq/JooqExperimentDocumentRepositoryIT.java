package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_DOCUMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every test creates the experiments it needs, other ITs commit documents to the seeded ones. The
 * experiment_documents RLS policies follow the experiment: readable documents for whoever may read
 * it, inserts for whoever may update it.
 */
@IT
class JooqExperimentDocumentRepositoryIT {

  private static final DocumentMetadata REPORT =
      new DocumentMetadata("report.pdf", "application/pdf", 42);

  @Autowired private JooqExperimentDocumentRepository repository;
  @Autowired private DSLContext dsl;

  @Test
  @Transactional
  void should_create_a_document_under_the_given_id_uploaded_now_by_the_given_user() {
    // given
    UUID experimentId = newExperiment();
    UUID documentId = callAsAdmin(repository::nextId);

    // when
    ExperimentDocument document =
        callAsAdmin(
            () ->
                repository.create(
                    documentId, experimentId, REPORT, SecurityContextTestSupport.USERNAME));

    // then
    assertThat(document.id()).isEqualTo(documentId);
    assertThat(document.experimentId()).isEqualTo(experimentId);
    assertThat(document.metadata()).isEqualTo(REPORT);
    assertThat(document.uploadedAt())
        .isCloseTo(OffsetDateTime.now(), within(1, ChronoUnit.MINUTES));
    assertThat(document.uploadedBy()).isEqualTo(SecurityContextTestSupport.USERNAME);
    assertThat(callAsAdmin(() -> repository.getById(experimentId, document.id())))
        .isEqualTo(document);
  }

  @Test
  void should_hand_out_a_new_id_on_every_call() {
    // when
    UUID first = callAsAdmin(repository::nextId);
    UUID second = callAsAdmin(repository::nextId);

    // then
    assertThat(first).isNotEqualTo(second);
    assertThat(first.version()).isEqualTo(7);
  }

  @Test
  @Transactional
  void should_list_the_documents_of_an_experiment_newest_first() {
    // given: the older one is inserted last, so it has the higher id, the order must come from
    // the upload time (both would share now() of the test transaction otherwise)
    UUID experimentId = newExperiment();
    ExperimentDocument newer =
        callAsAdmin(
            () ->
                repository.create(
                    repository.nextId(),
                    experimentId,
                    REPORT,
                    SecurityContextTestSupport.USERNAME));
    UUID older = insertDocumentUploadedAt(experimentId, OffsetDateTime.now().minusDays(1));
    callAsAdmin(
        () ->
            repository.create(
                repository.nextId(), newExperiment(), REPORT, SecurityContextTestSupport.USERNAME));

    // when
    List<ExperimentDocument> documents =
        callAsAdmin(() -> repository.findByExperimentId(experimentId));

    // then
    assertThat(documents).extracting(ExperimentDocument::id).containsExactly(newer.id(), older);
  }

  @Test
  @Transactional
  void should_not_find_a_document_under_another_experiment() {
    // given
    UUID otherExperimentId = newExperiment();
    ExperimentDocument document =
        callAsAdmin(
            () ->
                repository.create(
                    repository.nextId(),
                    newExperiment(),
                    REPORT,
                    SecurityContextTestSupport.USERNAME));

    // when / then
    assertThrows(
        ObjectNotFoundException.class,
        () -> callAsAdmin(() -> repository.getById(otherExperimentId, document.id())));
  }

  @Test
  @Transactional
  void should_not_find_an_unknown_document() {
    // given
    UUID experimentId = newExperiment();

    // when / then
    assertThrows(
        ObjectNotFoundException.class,
        () -> callAsAdmin(() -> repository.getById(experimentId, UUID.randomUUID())));
  }

  @Test
  @Transactional
  void should_hide_the_documents_of_an_experiment_the_user_may_not_read() {
    // given
    UUID readable = newExperiment();
    UUID hidden = newExperiment();
    ExperimentDocument readableDocument =
        callAsAdmin(
            () ->
                repository.create(
                    repository.nextId(), readable, REPORT, SecurityContextTestSupport.USERNAME));
    ExperimentDocument hiddenDocument =
        callAsAdmin(
            () ->
                repository.create(
                    repository.nextId(), hidden, REPORT, SecurityContextTestSupport.USERNAME));

    // when / then
    SecurityContextTestSupport.runAsUser(
        List.of(readable),
        () -> {
          assertThat(repository.findByExperimentId(readable))
              .extracting(ExperimentDocument::id)
              .containsExactly(readableDocument.id());
          assertThat(repository.findByExperimentId(hidden)).isEmpty();
          assertThrows(
              ObjectNotFoundException.class, () -> repository.getById(hidden, hiddenDocument.id()));
        });
  }

  @Test
  @Transactional
  void should_let_a_writer_add_documents_to_the_experiments_they_may_write() {
    // given
    UUID experimentId = newExperiment();

    // when / then
    SecurityContextTestSupport.runAsUser(
        List.of(experimentId),
        List.of(experimentId),
        () ->
            assertThat(
                    repository
                        .create(
                            repository.nextId(),
                            experimentId,
                            REPORT,
                            SecurityContextTestSupport.USERNAME)
                        .id())
                .isNotNull());
  }

  @Test
  @Transactional
  void should_reject_an_upload_by_a_user_who_may_only_read_the_experiment() {
    // given
    UUID experimentId = newExperiment();

    // when / then: the WITH CHECK of experiment_documents_insert aborts the transaction, so this
    // is the last statement of the test
    SecurityContextTestSupport.runAsUser(
        List.of(experimentId),
        () ->
            assertThrows(
                PermissionDeniedDataAccessException.class,
                () ->
                    repository.create(
                        repository.nextId(),
                        experimentId,
                        REPORT,
                        SecurityContextTestSupport.USERNAME)));
  }

  @Test
  @Transactional
  void should_reject_updating_a_document_even_for_an_admin() {
    // given
    ExperimentDocument document =
        callAsAdmin(
            () ->
                repository.create(
                    repository.nextId(),
                    newExperiment(),
                    REPORT,
                    SecurityContextTestSupport.USERNAME));

    // when: no UPDATE policy, RLS filters every row out
    int updated =
        callAsAdmin(
            () ->
                dsl.update(EXPERIMENT_DOCUMENTS)
                    .set(EXPERIMENT_DOCUMENTS.FILE_NAME, "renamed.pdf")
                    .where(EXPERIMENT_DOCUMENTS.ID.eq(document.id()))
                    .execute());

    // then
    assertThat(updated).isZero();
  }

  @Test
  @Transactional
  void should_reject_deleting_a_document_even_for_an_admin() {
    // given
    ExperimentDocument document =
        callAsAdmin(
            () ->
                repository.create(
                    repository.nextId(),
                    newExperiment(),
                    REPORT,
                    SecurityContextTestSupport.USERNAME));

    // when: no DELETE policy, RLS filters every row out
    int deleted =
        callAsAdmin(
            () ->
                dsl.deleteFrom(EXPERIMENT_DOCUMENTS)
                    .where(EXPERIMENT_DOCUMENTS.ID.eq(document.id()))
                    .execute());

    // then
    assertThat(deleted).isZero();
  }

  private UUID newExperiment() {
    return callAsAdmin(
        () ->
            dsl.insertInto(EXPERIMENTS)
                .set(EXPERIMENTS.NAME, "documents-it-" + UUID.randomUUID())
                .set(EXPERIMENTS.START, LocalDate.of(2030, 1, 1))
                .set(EXPERIMENTS.END, LocalDate.of(2030, 12, 31))
                .returning(EXPERIMENTS.ID)
                .fetchSingle()
                .getId());
  }

  private UUID insertDocumentUploadedAt(UUID experimentId, OffsetDateTime uploadedAt) {
    return callAsAdmin(
        () ->
            dsl.insertInto(EXPERIMENT_DOCUMENTS)
                .set(EXPERIMENT_DOCUMENTS.EXPERIMENT_ID, experimentId)
                .set(EXPERIMENT_DOCUMENTS.FILE_NAME, "older.pdf")
                .set(EXPERIMENT_DOCUMENTS.CONTENT_TYPE, "application/pdf")
                .set(EXPERIMENT_DOCUMENTS.SIZE_BYTES, 1L)
                .set(EXPERIMENT_DOCUMENTS.UPLOADED_AT, uploadedAt)
                .set(EXPERIMENT_DOCUMENTS.UPLOADED_BY, SecurityContextTestSupport.USERNAME)
                .returning(EXPERIMENT_DOCUMENTS.ID)
                .fetchSingle()
                .getId());
  }
}
