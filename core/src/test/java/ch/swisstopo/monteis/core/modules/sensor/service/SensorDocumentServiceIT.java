package ch.swisstopo.monteis.core.modules.sensor.service;

import static ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport.callAsAdmin;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_DOCUMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.itconfig.SeedData;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorDocumentStorage;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IT
class SensorDocumentServiceIT {

  @Autowired private SensorDocumentService service;
  @Autowired private DSLContext dsl;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private SensorDocumentStorage storage;

  @Test
  void should_let_a_pi_of_the_sensors_experiment_upload_a_document() {
    // given
    UUID sensorId = SeedData.SENSOR_ONLY_IN_ALPHA;
    String fileName = uniqueFileName();

    // when
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA),
        List.of(SeedData.EXPERIMENT_ALPHA),
        () -> service.upload(sensorId, pdfMetadata(fileName), InputStream.nullInputStream()));

    // then
    assertThat(countDocumentRows(fileName)).isEqualTo(1);
  }

  @Test
  void should_refuse_a_pi_of_another_experiment_before_storing_anything() {
    // given
    UUID sensorId = SeedData.SENSOR_ONLY_IN_ALPHA;
    String fileName = uniqueFileName();
    DocumentMetadata metadata = pdfMetadata(fileName);
    InputStream content = InputStream.nullInputStream();

    // when
    SecurityContextTestSupport.runAsUser(
        List.of(SeedData.EXPERIMENT_ALPHA, SeedData.EXPERIMENT_BETA),
        List.of(SeedData.EXPERIMENT_BETA),
        () ->
            assertThrows(
                AccessDeniedException.class, () -> service.upload(sensorId, metadata, content)));

    // then
    then(storage).shouldHaveNoInteractions();
    assertThat(countDocumentRows(fileName)).isZero();
  }

  @Test
  void should_record_no_document_row_when_storing_its_content_fails() {
    // given
    willThrow(new IllegalStateException("S3 unavailable"))
        .given(storage)
        .store(any(), any(), any());
    UUID sensorId = SeedData.SENSOR_ONLY_IN_ALPHA;
    String fileName = uniqueFileName();

    // when
    SecurityContextTestSupport.runAsAdmin(
        () ->
            assertThrows(
                IllegalStateException.class,
                () ->
                    service.upload(
                        sensorId, pdfMetadata(fileName), InputStream.nullInputStream())));

    // then
    assertThat(countDocumentRows(fileName)).isZero();
  }

  private static String uniqueFileName() {
    return "upload-" + UUID.randomUUID() + ".pdf";
  }

  private static DocumentMetadata pdfMetadata(String fileName) {
    return new DocumentMetadata(fileName, "application/pdf", 4);
  }

  private int countDocumentRows(String fileName) {
    return inTransactionAsAdmin(
        () -> dsl.fetchCount(SENSOR_DOCUMENTS, SENSOR_DOCUMENTS.FILE_NAME.eq(fileName)));
  }

  // RLS fails closed outside a transaction and would hide every row
  private <T> T inTransactionAsAdmin(Supplier<T> query) {
    return callAsAdmin(
        () -> new TransactionTemplate(transactionManager).execute(status -> query.get()));
  }
}
