package ch.swisstopo.monteis.core.modules.experiment.service;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_DOCUMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentStorage;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IT
class ExperimentDocumentServiceIT {

  @Autowired private ExperimentDocumentService service;
  @Autowired private DSLContext dsl;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private DocumentStorage storage;

  @Test
  void should_commit_the_document_row_when_its_content_is_stored() {
    // given
    String fileName = uniqueFileName();

    // when
    SecurityContextTestSupport.runAsAdmin(
        () ->
            service.upload(
                SeedData.EXPERIMENT_ALPHA, pdfMetadata(fileName), InputStream.nullInputStream()));

    // then
    assertThat(countDocumentRows(fileName)).isEqualTo(1);
  }

  @Test
  void should_record_no_document_row_when_storing_its_content_fails() {
    // given
    willThrow(new IllegalStateException("S3 unavailable"))
        .given(storage)
        .store(any(), any(), any(), any());
    String fileName = uniqueFileName();

    // when
    SecurityContextTestSupport.runAsAdmin(
        () ->
            assertThrows(
                IllegalStateException.class,
                () ->
                    service.upload(
                        SeedData.EXPERIMENT_ALPHA,
                        pdfMetadata(fileName),
                        InputStream.nullInputStream())));

    // then
    assertThat(countDocumentRows(fileName)).isZero();
  }

  @Test
  void should_delete_the_stored_content_when_row_level_security_rejects_the_upload() {
    // given
    String fileName = uniqueFileName();

    // when: read but no write access, the insert policy rejects the row
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA),
        () ->
            assertThrows(
                PermissionDeniedDataAccessException.class,
                () ->
                    service.upload(
                        SeedData.EXPERIMENT_ALPHA,
                        pdfMetadata(fileName),
                        InputStream.nullInputStream())));

    // then
    ArgumentCaptor<UUID> storedId = ArgumentCaptor.forClass(UUID.class);
    then(storage).should().store(eq(SeedData.EXPERIMENT_ALPHA), storedId.capture(), any(), any());
    then(storage).should().delete(SeedData.EXPERIMENT_ALPHA, storedId.getValue());
    assertThat(countDocumentRows(fileName)).isZero();
  }

  private static String uniqueFileName() {
    return "upload-" + UUID.randomUUID() + ".pdf";
  }

  private static DocumentMetadata pdfMetadata(String fileName) {
    return new DocumentMetadata(fileName, "application/pdf", 4);
  }

  // in a transaction of its own, RLS fails closed outside one and would always count 0
  private int countDocumentRows(String fileName) {
    return callAsAdmin(
        () ->
            new TransactionTemplate(transactionManager)
                .execute(
                    status ->
                        dsl.fetchCount(
                            EXPERIMENT_DOCUMENTS, EXPERIMENT_DOCUMENTS.FILE_NAME.eq(fileName))));
  }
}
