package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentTooLargeException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentSyncServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private static final OffsetDateTime SYNCED_AT = NOW.atOffset(ZoneOffset.UTC);
  private static final Instant DEADLINE = NOW.plus(Duration.ofMinutes(8));
  private static final UUID RECORD_ID = UUID.fromString("221ae592-4527-4442-8294-8dc2c5be1168");
  private static final UUID ATTACHMENT_ID = UUID.fromString("22c52651-0b4b-48ca-bf8e-6594e935da0f");
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final String TITLE = "DS Experiment: BDS-1";
  private static final FulcrumLiteratureRow ROW = row(RECORD_ID, TITLE, ATTACHMENT_ID);

  @Mock private FulcrumService fulcrumService;
  @Mock private FulcrumDocumentImportService importService;
  @Mock private FulcrumDocumentRepository documentRepository;
  @Mock private FulcrumSyncRepository syncRepository;

  private MutableClock clock;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(NOW);
  }

  @Test
  void should_save_the_metadata_fulcrum_returned_without_importing_the_file_again() {
    // given
    FulcrumLiteratureRow retitled = row(RECORD_ID, "DS Experiment: BDS-1 (revised)", ATTACHMENT_ID);
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(retitled));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID)));

    // when
    service().sync(DEADLINE);

    // then
    then(documentRepository)
        .should()
        .saveSynced(withTitle(stored(ATTACHMENT_ID), "DS Experiment: BDS-1 (revised)"));
    then(documentRepository).should().markRemovedFromFulcrum(List.of(), SYNCED_AT);
    then(importService).shouldHaveNoInteractions();
    then(syncRepository).should().saveSuccessfulSync(SYNCED_AT);
  }

  @Test
  void should_mark_stored_documents_fulcrum_no_longer_returns_as_removed() {
    // given
    FulcrumDocument alreadyRemoved = removed(UUID.randomUUID());
    FulcrumLiteratureRow other = row(UUID.randomUUID(), TITLE, ATTACHMENT_ID);
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(other));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID), alreadyRemoved));

    // when
    service().sync(DEADLINE);

    // then
    then(documentRepository).should().markRemovedFromFulcrum(List.of(RECORD_ID), SYNCED_AT);
  }

  @Test
  void should_mark_nothing_as_removed_and_not_succeed_when_fulcrum_returns_no_literature() {
    // given
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of());

    // when
    service().sync(DEADLINE);

    // then
    then(documentRepository).shouldHaveNoInteractions();
    then(syncRepository).should(never()).saveSuccessfulSync(any());
  }

  @Test
  void should_import_the_file_of_a_new_document() {
    // given
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));

    // when
    service().sync(DEADLINE);

    // then
    then(importService).should().importFulcrumDocument(ROW);
    then(syncRepository).should().saveSuccessfulSync(SYNCED_AT);
  }

  @Test
  void should_import_a_replaced_file_and_save_its_document_as_synced_right_away() {
    // given
    UUID oldAttachment = UUID.randomUUID();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));
    given(documentRepository.findAll()).willReturn(List.of(stored(oldAttachment)));

    // when
    service().sync(DEADLINE);

    // then keeps old file until new one imported
    then(documentRepository).should().saveSynced(stored(oldAttachment));
    then(importService).should().importFulcrumDocument(ROW);
  }

  @Test
  void should_take_the_first_file_of_a_document_with_several() {
    // given
    FulcrumLiteratureRow withTwoFiles = row(RECORD_ID, TITLE, ATTACHMENT_ID, UUID.randomUUID());
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(withTwoFiles));

    // when
    service().sync(DEADLINE);

    // then
    then(importService).should().importFulcrumDocument(withTwoFiles);
  }

  @Test
  void should_go_on_with_the_other_files_when_one_import_fails_and_not_succeed() {
    // given
    FulcrumLiteratureRow broken = row(UUID.randomUUID(), TITLE, UUID.randomUUID());
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(broken, ROW));
    willThrow(new IllegalStateException("download failed"))
        .given(importService)
        .importFulcrumDocument(broken);

    // when
    service().sync(DEADLINE);

    // then
    then(importService).should().importFulcrumDocument(ROW);
    then(syncRepository).should(never()).saveSuccessfulSync(any());
  }

  @Test
  void should_go_on_with_the_other_documents_when_one_cannot_be_saved() {
    // given
    UUID otherId = UUID.randomUUID();
    FulcrumDocument other = withId(stored(ATTACHMENT_ID), otherId);
    given(fulcrumService.findLiteratureMarkedForMonteis())
        .willReturn(List.of(ROW, row(otherId, TITLE, ATTACHMENT_ID)));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID), other));
    willThrow(new IllegalStateException("value too long"))
        .given(documentRepository)
        .saveSynced(stored(ATTACHMENT_ID));

    // when
    service().sync(DEADLINE);

    // then
    then(documentRepository).should().saveSynced(other);
    then(syncRepository).should(never()).saveSuccessfulSync(any());
  }

  @Test
  void should_skip_a_file_too_large_without_failing_the_sync() {
    // given
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));
    willThrow(new FulcrumAttachmentTooLargeException(ATTACHMENT_ID, DataSize.ofMegabytes(200)))
        .given(importService)
        .importFulcrumDocument(ROW);

    // when
    service().sync(DEADLINE);

    // then
    then(syncRepository).should().saveSuccessfulSync(SYNCED_AT);
  }

  @Test
  void should_stop_importing_once_the_deadline_is_passed() {
    // given
    FulcrumLiteratureRow second = row(UUID.randomUUID(), TITLE, UUID.randomUUID());
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW, second));
    willAnswer(invocation -> clock.advanceBy(Duration.ofMinutes(8).plusSeconds(1)))
        .given(importService)
        .importFulcrumDocument(ROW);

    // when
    service().sync(DEADLINE);

    // then
    then(importService).should().importFulcrumDocument(ROW);
    then(importService).should(never()).importFulcrumDocument(second);
    then(syncRepository).should(never()).saveSuccessfulSync(any());
  }

  @Test
  void should_change_nothing_and_fail_when_fulcrum_cannot_be_queried() {
    // given
    given(fulcrumService.findLiteratureMarkedForMonteis())
        .willThrow(
            new RestClientResponseException("bad gateway", 502, "Bad Gateway", null, null, null));
    FulcrumDocumentSyncService service = service();

    // when / then
    assertThrows(RestClientResponseException.class, () -> service.sync(DEADLINE));
    then(documentRepository).shouldHaveNoInteractions();
    then(importService).shouldHaveNoInteractions();
    then(syncRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_not_save_a_successful_sync_when_it_dies_after_saving_the_metadata() {
    // given
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID)));
    willThrow(new IllegalStateException("connection lost"))
        .given(documentRepository)
        .markRemovedFromFulcrum(List.of(), SYNCED_AT);
    FulcrumDocumentSyncService service = service();

    // when / then
    assertThrows(IllegalStateException.class, () -> service.sync(DEADLINE));
    then(documentRepository).should().saveSynced(stored(ATTACHMENT_ID));
    then(syncRepository).should(never()).saveSuccessfulSync(any());
  }

  private FulcrumDocumentSyncService service() {
    return new FulcrumDocumentSyncService(
        fulcrumService,
        importService,
        documentRepository,
        syncRepository,
        new FulcrumLiteratureMapperImpl(),
        clock);
  }

  private static FulcrumLiteratureRow row(UUID recordId, String title, UUID... attachmentIds) {
    return new FulcrumLiteratureRow(
        recordId,
        "2011-02",
        title,
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"),
        List.of(attachmentIds),
        Arrays.stream(attachmentIds).map(id -> FILE_NAME).toList());
  }

  private static FulcrumDocument removed(UUID id) {
    FulcrumDocument document = withId(stored(UUID.randomUUID()), id);
    return new FulcrumDocument(
        document.id(),
        document.number(),
        document.title(),
        document.authors(),
        document.year(),
        document.category(),
        document.keywords(),
        document.file(),
        SYNCED_AT.minusDays(3));
  }

  private static FulcrumDocument withId(FulcrumDocument document, UUID id) {
    return new FulcrumDocument(
        id,
        document.number(),
        document.title(),
        document.authors(),
        document.year(),
        document.category(),
        document.keywords(),
        document.file(),
        document.removedAt());
  }

  private static FulcrumDocument withTitle(FulcrumDocument document, String title) {
    return new FulcrumDocument(
        document.id(),
        document.number(),
        title,
        document.authors(),
        document.year(),
        document.category(),
        document.keywords(),
        document.file(),
        document.removedAt());
  }

  private static FulcrumDocument stored(UUID attachmentId) {
    return new FulcrumDocument(
        RECORD_ID,
        "2011-02",
        TITLE,
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"),
        new FulcrumDocumentFile(
            attachmentId, new DocumentMetadata(FILE_NAME, "application/pdf", 8)),
        null);
  }

  // deadline checked before each import, an import advances the clock
  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private Void advanceBy(Duration duration) {
      now = now.plus(duration);
      return null;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
