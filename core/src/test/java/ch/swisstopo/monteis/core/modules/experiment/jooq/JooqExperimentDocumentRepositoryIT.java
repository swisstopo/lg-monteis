package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_DOCUMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentUpload;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against the dev seed data ({@code db/meta/seed/R__seed_dev_data.sql}). The
 * experiment_documents RLS policies follow the experiment: readable documents for whoever may read
 * it, inserts for whoever may update it.
 */
@IT
class JooqExperimentDocumentRepositoryIT {

  private static final UUID EXPERIMENT_ALPHA =
      UUID.fromString("00000000-0000-7000-8000-000000000301");
  private static final UUID EXPERIMENT_BETA =
      UUID.fromString("00000000-0000-7000-8000-000000000302");
  private static final DocumentUpload REPORT =
      new DocumentUpload("report.pdf", "application/pdf", 42);

  @Autowired private JooqExperimentDocumentRepository repository;
  @Autowired private DSLContext dsl;

  @Test
  @Transactional
  void should_create_a_document_uploaded_now_by_the_current_user() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          ExperimentDocument document = repository.create(EXPERIMENT_ALPHA, REPORT);

          assertThat(document.id()).isNotNull();
          assertThat(document.experimentId()).isEqualTo(EXPERIMENT_ALPHA);
          assertThat(document.fileName()).isEqualTo("report.pdf");
          assertThat(document.contentType()).isEqualTo("application/pdf");
          assertThat(document.sizeBytes()).isEqualTo(42);
          assertThat(document.uploadedAt()).isNotNull();
          assertThat(document.uploadedBy()).isEqualTo("test");
          assertThat(repository.getById(EXPERIMENT_ALPHA, document.id())).isEqualTo(document);
        });
  }

  @Test
  @Transactional
  void should_list_the_documents_of_an_experiment_newest_first() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          ExperimentDocument older = repository.create(EXPERIMENT_ALPHA, REPORT);
          ExperimentDocument newer =
              repository.create(EXPERIMENT_ALPHA, new DocumentUpload("plan.txt", "text/plain", 1));
          repository.create(EXPERIMENT_BETA, REPORT);

          assertThat(repository.findByExperimentId(EXPERIMENT_ALPHA))
              .extracting(ExperimentDocument::id)
              .containsExactly(newer.id(), older.id());
        });
  }

  @Test
  @Transactional
  void should_not_find_a_document_under_another_experiment() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          ExperimentDocument document = repository.create(EXPERIMENT_ALPHA, REPORT);

          assertThrows(
              ObjectNotFoundException.class,
              () -> repository.getById(EXPERIMENT_BETA, document.id()));
        });
  }

  @Test
  @Transactional
  void should_not_find_an_unknown_document() {
    SecurityContextTestSupport.runAsAdmin(
        () ->
            assertThrows(
                ObjectNotFoundException.class,
                () -> repository.getById(EXPERIMENT_ALPHA, UUID.randomUUID())));
  }

  @Test
  @Transactional
  void should_hide_the_documents_of_an_experiment_the_user_may_not_read() {
    AtomicReference<ExperimentDocument> betaDocument = new AtomicReference<>();
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          repository.create(EXPERIMENT_ALPHA, REPORT);
          betaDocument.set(repository.create(EXPERIMENT_BETA, REPORT));
        });

    SecurityContextTestSupport.runAsUser(
        List.of(EXPERIMENT_ALPHA),
        () -> {
          assertThat(repository.findByExperimentId(EXPERIMENT_ALPHA)).hasSize(1);
          assertThat(repository.findByExperimentId(EXPERIMENT_BETA)).isEmpty();
          assertThrows(
              ObjectNotFoundException.class,
              () -> repository.getById(EXPERIMENT_BETA, betaDocument.get().id()));
        });
  }

  @Test
  @Transactional
  void should_let_a_writer_add_documents_to_the_experiments_they_may_write() {
    SecurityContextTestSupport.runAsUser(
        List.of(EXPERIMENT_ALPHA),
        List.of(EXPERIMENT_ALPHA),
        () -> assertThat(repository.create(EXPERIMENT_ALPHA, REPORT).id()).isNotNull());
  }

  @Test
  @Transactional
  void should_reject_an_upload_by_a_user_who_may_only_read_the_experiment() {
    // the WITH CHECK of experiment_documents_insert aborts the transaction, so this is the only
    // statement of the test
    SecurityContextTestSupport.runAsUser(
        List.of(EXPERIMENT_ALPHA),
        () ->
            assertThrows(
                PermissionDeniedDataAccessException.class,
                () -> repository.create(EXPERIMENT_ALPHA, REPORT)));
  }

  @Test
  @Transactional
  void should_reject_updating_and_deleting_documents_for_everyone() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          ExperimentDocument document = repository.create(EXPERIMENT_ALPHA, REPORT);

          // no UPDATE or DELETE policy: RLS filters every row out, nothing changes
          assertThat(
                  dsl.update(EXPERIMENT_DOCUMENTS)
                      .set(EXPERIMENT_DOCUMENTS.FILE_NAME, "renamed.pdf")
                      .where(EXPERIMENT_DOCUMENTS.ID.eq(document.id()))
                      .execute())
              .isZero();
          assertThat(
                  dsl.deleteFrom(EXPERIMENT_DOCUMENTS)
                      .where(EXPERIMENT_DOCUMENTS.ID.eq(document.id()))
                      .execute())
              .isZero();
        });
  }
}
