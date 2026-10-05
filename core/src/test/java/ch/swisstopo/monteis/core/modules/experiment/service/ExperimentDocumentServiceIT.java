package ch.swisstopo.monteis.core.modules.experiment.service;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_DOCUMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentMetadata;
import ch.swisstopo.monteis.core.modules.experiment.domain.DocumentStorage;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The upload transaction: a failed store must not leave a document row without content. Not
 * {@code @Transactional} itself, the upload has to commit or roll back on its own.
 */
@IT
class ExperimentDocumentServiceIT {

  private static final UUID EXPERIMENT_ALPHA =
      UUID.fromString("00000000-0000-7000-8000-000000000301");

  @Autowired private ExperimentDocumentService service;
  @Autowired private DSLContext dsl;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private DocumentStorage storage;

  @Test
  void should_commit_the_document_row_when_its_content_is_stored() {
    String fileName = uniqueFileName();

    SecurityContextTestSupport.runAsAdmin(
        () ->
            service.upload(EXPERIMENT_ALPHA, pdfMetadata(fileName), InputStream.nullInputStream()));

    assertThat(countDocumentRows(fileName)).isEqualTo(1);
  }

  @Test
  void should_roll_the_document_row_back_when_storing_its_content_fails() {
    // given
    willThrow(new IllegalStateException("S3 unavailable")).given(storage).store(any(), any());
    String fileName = uniqueFileName();

    // when
    SecurityContextTestSupport.runAsAdmin(
        () ->
            assertThrows(
                IllegalStateException.class,
                () ->
                    service.upload(
                        EXPERIMENT_ALPHA, pdfMetadata(fileName), InputStream.nullInputStream())));

    // then
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
    AtomicInteger count = new AtomicInteger();
    SecurityContextTestSupport.runAsAdmin(
        () ->
            new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                    status ->
                        count.set(
                            dsl.fetchCount(
                                EXPERIMENT_DOCUMENTS,
                                EXPERIMENT_DOCUMENTS.FILE_NAME.eq(fileName)))));
    return count.get();
  }
}
