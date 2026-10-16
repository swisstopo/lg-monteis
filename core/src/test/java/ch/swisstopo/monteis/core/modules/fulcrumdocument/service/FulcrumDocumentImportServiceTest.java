package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentTooLargeException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  private static final FulcrumAttachmentReference ATTACHMENT =
      new FulcrumAttachmentReference(ATTACHMENT_ID, FILE_NAME);

  @Mock private FulcrumAttachmentDownloadClient downloadClient;
  @Mock private FulcrumDocumentStorage storage;
  @Mock private FulcrumDocumentRepository documentRepository;

  private FulcrumDocumentImportService importService;

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
  void should_store_the_file_before_saving_the_document_and_delete_the_download() {
    // given
    givenTheDownloadReturns(CONTENT);
    List<byte[]> stored = new ArrayList<>();
    List<Path> downloads = new ArrayList<>();
    willAnswer(
            invocation -> {
              Path download = invocation.getArgument(2, Path.class);
              downloads.add(download);
              return stored.add(Files.readAllBytes(download));
            })
        .given(storage)
        .store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));

    // when
    importService.importFile(new FileToImport(ROW, ATTACHMENT));

    // then
    assertThat(stored).singleElement().isEqualTo(CONTENT);
    assertThat(downloads).singleElement().matches(Files::notExists);
    InOrder order = inOrder(storage, documentRepository);
    order.verify(storage).store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));
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
        IllegalStateException.class,
        () -> importService.importFile(new FileToImport(ROW, ATTACHMENT)));
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
        () -> importService.importFile(new FileToImport(ROW, ATTACHMENT)));
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_not_save_the_document_when_storing_fails() {
    // given
    givenTheDownloadReturns(CONTENT);
    willThrow(new IllegalStateException("S3 down"))
        .given(storage)
        .store(eq(ATTACHMENT_ID), any(), any(Path.class));

    // when / then
    assertThrows(
        IllegalStateException.class,
        () -> importService.importFile(new FileToImport(ROW, ATTACHMENT)));
    then(documentRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_delete_the_download_when_saving_the_document_fails() {
    // given
    givenTheDownloadReturns(CONTENT);
    ArgumentCaptor<Path> download = ArgumentCaptor.forClass(Path.class);
    willThrow(new IllegalStateException("db down")).given(documentRepository).saveSynced(any());

    // when / then
    assertThrows(
        IllegalStateException.class,
        () -> importService.importFile(new FileToImport(ROW, ATTACHMENT)));
    then(storage).should().store(eq(ATTACHMENT_ID), eq("application/pdf"), download.capture());
    assertThat(download.getValue()).doesNotExist();
  }

  @Test
  void should_not_import_an_empty_file() {
    // given
    givenTheDownloadReturns(new byte[0]);

    // when / then
    assertThrows(
        ObjectBusinessValidationException.class,
        () -> importService.importFile(new FileToImport(ROW, ATTACHMENT)));
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_store_an_unknown_file_type_as_octet_stream() {
    // given
    givenTheDownloadReturns(CONTENT);

    // when
    importService.importFile(
        new FileToImport(ROW, new FulcrumAttachmentReference(ATTACHMENT_ID, "notes.unknownext")));

    // then
    then(storage)
        .should()
        .store(eq(ATTACHMENT_ID), eq("application/octet-stream"), any(Path.class));
  }

  @Test
  void should_store_a_type_the_browser_would_run_as_octet_stream() {
    // given
    givenTheDownloadReturns(CONTENT);

    // when
    importService.importFile(
        new FileToImport(ROW, new FulcrumAttachmentReference(ATTACHMENT_ID, "map.svg")));

    // then
    then(storage)
        .should()
        .store(eq(ATTACHMENT_ID), eq("application/octet-stream"), any(Path.class));
  }

  @Test
  void should_store_a_viewable_image_with_its_type() {
    // given
    givenTheDownloadReturns(CONTENT);

    // when
    importService.importFile(
        new FileToImport(ROW, new FulcrumAttachmentReference(ATTACHMENT_ID, "drilling.png")));

    // then
    then(storage).should().store(eq(ATTACHMENT_ID), eq("image/png"), any(Path.class));
  }

  @Test
  void should_cut_a_long_file_name_but_keep_its_extension() {
    // given
    givenTheDownloadReturns(CONTENT);
    String longName = "a".repeat(300) + ".pdf";

    // when
    importService.importFile(
        new FileToImport(ROW, new FulcrumAttachmentReference(ATTACHMENT_ID, longName)));

    // then
    ArgumentCaptor<FulcrumDocument> saved = ArgumentCaptor.forClass(FulcrumDocument.class);
    then(documentRepository).should().saveSynced(saved.capture());
    DocumentMetadata metadata = saved.getValue().file().metadata();
    assertThat(metadata.fileName())
        .hasSize(DocumentMetadata.MAX_LENGTH)
        .endsWith(".pdf")
        .startsWith("aaa");
    assertThat(metadata.contentType()).isEqualTo("application/pdf");
  }

  private void givenTheDownloadReturns(byte[] content) {
    given(downloadClient.download(ATTACHMENT_ID, MAX_FILE_SIZE))
        .willReturn(new ByteArrayInputStream(content));
  }
}
