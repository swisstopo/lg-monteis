package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownload;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentTooLargeException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentImportServiceTest {

  private static final UUID RECORD_ID = UUID.randomUUID();
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final DataSize MAX_FILE_SIZE = DataSize.ofMegabytes(200);
  private static final Duration TIME_LEFT = Duration.ofMinutes(3);
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final FulcrumLiteratureRow ROW =
      new FulcrumLiteratureRow(
          RECORD_ID,
          "2011-02",
          "DS Experiment",
          "Jahns E.",
          2011,
          "TN",
          List.of("RACOS"),
          List.of(ATTACHMENT_ID),
          List.of(FILE_NAME));

  @Mock private FulcrumAttachmentDownloadClient downloadClient;
  @Mock private FulcrumDocumentStorage storage;
  @Mock private FulcrumDocumentRepository documentRepository;

  private FulcrumDocumentImportService importService;
  private boolean downloadClosed;

  @BeforeEach
  void createImportService() {
    importService =
        new FulcrumDocumentImportService(
            downloadClient,
            storage,
            documentRepository,
            new FulcrumLiteratureMapperImpl(),
            new FulcrumDocumentSyncProperties(
                Duration.ofMinutes(8), Duration.ofMinutes(2), MAX_FILE_SIZE));
  }

  @Test
  void should_store_the_file_before_saving_the_document_and_close_the_download() {
    // given
    givenTheDownloadReturns(CONTENT);
    List<byte[]> stored = new ArrayList<>();
    willAnswer(
            invocation -> stored.add(invocation.getArgument(1, InputStream.class).readAllBytes()))
        .given(storage)
        .store(fileOfType("application/pdf"), any(), eq(TIME_LEFT));

    // when
    importService.importFulcrumDocument(ROW, TIME_LEFT);

    // then
    assertThat(stored).singleElement().isEqualTo(CONTENT);
    assertThat(downloadClosed).isTrue();
    InOrder order = inOrder(storage, documentRepository);
    order.verify(storage).store(fileOfType("application/pdf"), any(), eq(TIME_LEFT));
    order
        .verify(documentRepository)
        .saveSynced(
            new FulcrumDocument(
                RECORD_ID,
                "2011-02",
                "DS Experiment",
                "Jahns E.",
                2011,
                "TN",
                List.of("RACOS"),
                new FulcrumDocumentFile(
                    ATTACHMENT_ID,
                    new DocumentMetadata(FILE_NAME, "application/pdf", CONTENT.length)),
                null));
  }

  @Test
  void should_not_save_the_document_when_the_download_fails() {
    // given
    given(downloadClient.download(ATTACHMENT_ID, MAX_FILE_SIZE))
        .willThrow(new IllegalStateException("answered 404"));

    // when / then
    assertThrows(
        IllegalStateException.class, () -> importService.importFulcrumDocument(ROW, TIME_LEFT));
    then(storage).shouldHaveNoInteractions();
    then(documentRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_pass_on_a_file_too_large_without_storing_it() {
    // given
    given(downloadClient.download(ATTACHMENT_ID, MAX_FILE_SIZE))
        .willThrow(new FulcrumAttachmentTooLargeException(ATTACHMENT_ID, MAX_FILE_SIZE));

    // when / then
    assertThrows(
        FulcrumAttachmentTooLargeException.class,
        () -> importService.importFulcrumDocument(ROW, TIME_LEFT));
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_not_save_the_document_when_storing_fails() {
    // given
    givenTheDownloadReturns(CONTENT);
    willThrow(new IllegalStateException("S3 down")).given(storage).store(any(), any(), any());

    // when / then
    assertThrows(
        IllegalStateException.class, () -> importService.importFulcrumDocument(ROW, TIME_LEFT));
    then(documentRepository).shouldHaveNoInteractions();
    assertThat(downloadClosed).isTrue();
  }

  @Test
  void should_not_import_an_empty_file() {
    // given
    givenTheDownloadReturns(new byte[0]);

    // when / then
    assertThrows(
        ObjectBusinessValidationException.class,
        () -> importService.importFulcrumDocument(ROW, TIME_LEFT));
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_row_without_a_file_before_downloading_anything() {
    // given
    FulcrumLiteratureRow withoutFile =
        new FulcrumLiteratureRow(
            RECORD_ID, "2011-02", "DS Experiment", "Jahns E.", 2011, "TN", null, null, null);

    // when / then
    assertThrows(
        IllegalArgumentException.class,
        () -> importService.importFulcrumDocument(withoutFile, TIME_LEFT));
    then(downloadClient).shouldHaveNoInteractions();
  }

  private void givenTheDownloadReturns(byte[] content) {
    InputStream stream =
        new ByteArrayInputStream(content) {
          @Override
          public void close() {
            downloadClosed = true;
          }
        };
    given(downloadClient.download(ATTACHMENT_ID, MAX_FILE_SIZE))
        .willReturn(new FulcrumAttachmentDownload(stream, content.length));
  }

  private static FulcrumDocumentFile fileOfType(String contentType) {
    return argThat(
        file ->
            file.attachmentId().equals(ATTACHMENT_ID)
                && file.metadata().contentType().equals(contentType));
  }
}
