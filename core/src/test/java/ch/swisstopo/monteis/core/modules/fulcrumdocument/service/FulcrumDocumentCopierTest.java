package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentCopierTest {

  private static final OffsetDateTime SEEN_AT =
      OffsetDateTime.of(2026, 10, 15, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID RECORD_ID = UUID.randomUUID();
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final FulcrumLiteratureEntry ENTRY =
      new FulcrumLiteratureEntry(
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

  private FulcrumDocumentCopier copier;

  @BeforeEach
  void createCopier() {
    copier =
        new FulcrumDocumentCopier(
            downloadClient, storage, documentRepository, new FulcrumLiteratureMapperImpl());
  }

  @Test
  void should_store_the_file_before_saving_the_document() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));
    List<byte[]> stored = new ArrayList<>();
    willAnswer(invocation -> stored.add(Files.readAllBytes(invocation.getArgument(2, Path.class))))
        .given(storage)
        .store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));

    // when
    boolean copied = copier.copyQuietly(ENTRY, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isTrue();
    assertThat(stored).singleElement().isEqualTo(CONTENT);
    InOrder order = inOrder(storage, documentRepository);
    order.verify(storage).store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));
    order
        .verify(documentRepository)
        .saveSeen(
            List.of(
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
                    null)),
            SEEN_AT);
  }

  @Test
  void should_not_save_the_document_when_the_download_fails() {
    // given
    given(downloadClient.download(ATTACHMENT_ID))
        .willThrow(new IllegalStateException("answered 404"));

    // when
    boolean copied = copier.copyQuietly(ENTRY, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isFalse();
    then(storage).shouldHaveNoInteractions();
    then(documentRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_not_save_the_document_when_storing_fails() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));
    willThrow(new IllegalStateException("S3 down"))
        .given(storage)
        .store(eq(ATTACHMENT_ID), any(), any(Path.class));

    // when
    boolean copied = copier.copyQuietly(ENTRY, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isFalse();
    then(documentRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_not_copy_an_empty_file() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(new byte[0]));

    // when
    boolean copied = copier.copyQuietly(ENTRY, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isFalse();
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_store_an_unknown_file_type_as_octet_stream() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));

    // when
    copier.copyQuietly(
        ENTRY, new FulcrumAttachmentReference(ATTACHMENT_ID, "notes.unknownext"), SEEN_AT);

    // then
    then(storage)
        .should()
        .store(eq(ATTACHMENT_ID), eq("application/octet-stream"), any(Path.class));
  }
}
