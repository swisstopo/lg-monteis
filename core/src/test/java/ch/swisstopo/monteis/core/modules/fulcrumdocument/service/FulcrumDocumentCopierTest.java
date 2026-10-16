package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentDownloadClient;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumCitation;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentCopierTest {

  private static final OffsetDateTime SEEN_AT =
      OffsetDateTime.of(2026, 10, 15, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final FulcrumCitation CITATION =
      new FulcrumCitation(
          UUID.randomUUID(), "2011-02", "DS Experiment", "Jahns E.", 2011, "TN", List.of());
  private static final FulcrumAttachmentReference ATTACHMENT =
      new FulcrumAttachmentReference(ATTACHMENT_ID, FILE_NAME);

  @Mock private FulcrumAttachmentDownloadClient downloadClient;
  @Mock private FulcrumDocumentStorage storage;
  @Mock private FulcrumEntryRepository entryRepository;

  @InjectMocks private FulcrumDocumentCopier copier;

  @Test
  void should_store_the_content_before_saving_the_entry() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));
    List<byte[]> stored = new ArrayList<>();
    willAnswer(invocation -> stored.add(Files.readAllBytes(invocation.getArgument(2, Path.class))))
        .given(storage)
        .store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));

    // when
    boolean copied = copier.copyQuietly(CITATION, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isTrue();
    assertThat(stored).singleElement().isEqualTo(CONTENT);
    InOrder order = inOrder(storage, entryRepository);
    order.verify(storage).store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));
    order
        .verify(entryRepository)
        .saveWithDocument(
            CITATION,
            new FulcrumDocument(
                ATTACHMENT_ID, new DocumentMetadata(FILE_NAME, "application/pdf", CONTENT.length)),
            SEEN_AT);
  }

  @Test
  void should_not_save_the_entry_when_the_download_fails() {
    // given
    given(downloadClient.download(ATTACHMENT_ID))
        .willThrow(new IllegalStateException("answered 404"));

    // when
    boolean copied = copier.copyQuietly(CITATION, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isFalse();
    then(storage).shouldHaveNoInteractions();
    then(entryRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_not_save_the_entry_when_storing_fails() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));
    willThrow(new IllegalStateException("S3 down"))
        .given(storage)
        .store(eq(ATTACHMENT_ID), any(), any(Path.class));

    // when
    boolean copied = copier.copyQuietly(CITATION, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isFalse();
    then(entryRepository).should(never()).saveWithDocument(any(), any(), any());
  }

  @Test
  void should_not_copy_an_empty_file() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(new byte[0]));

    // when
    boolean copied = copier.copyQuietly(CITATION, ATTACHMENT, SEEN_AT);

    // then
    assertThat(copied).isFalse();
    then(storage).shouldHaveNoInteractions();
  }

  @Test
  void should_derive_an_unknown_content_type_as_octet_stream() {
    // given
    given(downloadClient.download(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));

    // when
    copier.copyQuietly(
        CITATION, new FulcrumAttachmentReference(ATTACHMENT_ID, "notes.unknownext"), SEEN_AT);

    // then
    then(storage)
        .should()
        .store(eq(ATTACHMENT_ID), eq("application/octet-stream"), any(Path.class));
  }
}
