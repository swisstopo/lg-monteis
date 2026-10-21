package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachmentTooLargeException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDisabledException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentSyncServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private static final OffsetDateTime SYNCED_AT = NOW.atOffset(ZoneOffset.UTC);
  private static final Duration TIME_BUDGET = Duration.ofMinutes(8);
  private static final boolean ENABLED = true;
  private static final boolean DISABLED = false;
  private static final UUID RECORD_ID = UUID.fromString("221ae592-4527-4442-8294-8dc2c5be1168");
  private static final UUID ATTACHMENT_ID = UUID.fromString("22c52651-0b4b-48ca-bf8e-6594e935da0f");
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final String TITLE = "DS Experiment: BDS-1";
  private static final FulcrumLiteratureRow ROW = row(RECORD_ID, TITLE, ATTACHMENT_ID);

  @Mock private FulcrumService fulcrumService;
  @Mock private FulcrumDocumentImportService importService;
  @Mock private FulcrumDocumentRepository documentRepository;
  @Mock private FulcrumSyncRepository syncRepository;
  @Mock private FulcrumSyncLock syncLock;
  @Mock private SimpleLock lock;

  private MutableClock clock;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(NOW);
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void should_save_the_metadata_fulcrum_returned_without_importing_the_file_again() {
    // given
    FulcrumLiteratureRow retitled = row(RECORD_ID, "DS Experiment: BDS-1 (revised)", ATTACHMENT_ID);
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(retitled));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID)));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository)
        .should()
        .saveSynced(withTitle(stored(ATTACHMENT_ID), "DS Experiment: BDS-1 (revised)"));
    then(documentRepository).should().markRemovedFromFulcrum(List.of(), SYNCED_AT);
    then(importService).shouldHaveNoInteractions();
    then(syncRepository).should().saveSuccessfulSync(SYNCED_AT);
    assertThat(syncs("success")).isEqualTo(1);
    then(lock).should().unlock();
  }

  @Test
  void should_mark_stored_documents_fulcrum_no_longer_returns_as_removed() {
    // given
    FulcrumDocument alreadyRemoved = removed(UUID.randomUUID());
    FulcrumLiteratureRow other = row(UUID.randomUUID(), TITLE, ATTACHMENT_ID);
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(other));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID), alreadyRemoved));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).should().markRemovedFromFulcrum(List.of(RECORD_ID), SYNCED_AT);
  }

  @Test
  void should_mark_nothing_as_removed_and_fail_when_fulcrum_returns_no_literature() {
    // given
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of());

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).shouldHaveNoInteractions();
    then(syncRepository).should(never()).saveSuccessfulSync(any());
    assertThat(syncs("failed")).isEqualTo(1);
    then(lock).should().unlock();
  }

  @Test
  void should_import_the_file_of_a_new_document() {
    // given
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(importService).should().importFulcrumDocument(ROW);
    then(syncRepository).should().saveSuccessfulSync(SYNCED_AT);
  }

  @Test
  void should_import_a_replaced_file_and_save_its_document_as_synced_right_away() {
    // given
    UUID oldAttachment = UUID.randomUUID();
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));
    given(documentRepository.findAll()).willReturn(List.of(stored(oldAttachment)));

    // when
    service(ENABLED).syncUnlessRunning();

    // then the document keeps its old file until the new one is imported
    then(documentRepository).should().saveSynced(stored(oldAttachment));
    then(importService).should().importFulcrumDocument(ROW);
  }

  @Test
  void should_take_the_first_file_of_a_document_with_several() {
    // given
    FulcrumLiteratureRow withTwoFiles = row(RECORD_ID, TITLE, ATTACHMENT_ID, UUID.randomUUID());
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(withTwoFiles));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(importService).should().importFulcrumDocument(withTwoFiles);
  }

  @Test
  void should_go_on_with_the_other_files_when_one_import_fails_and_count_the_sync_as_failed() {
    // given
    FulcrumLiteratureRow broken = row(UUID.randomUUID(), TITLE, UUID.randomUUID());
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(broken, ROW));
    willThrow(new IllegalStateException("download failed"))
        .given(importService)
        .importFulcrumDocument(broken);

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(importService).should().importFulcrumDocument(ROW);
    then(syncRepository).should(never()).saveSuccessfulSync(any());
    assertThat(syncs("failed")).isEqualTo(1);
    assertThat(failedFiles()).isEqualTo(1);
  }

  @Test
  void should_go_on_with_the_other_documents_when_one_cannot_be_saved() {
    // given
    UUID otherId = UUID.randomUUID();
    FulcrumDocument other = withId(stored(ATTACHMENT_ID), otherId);
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis())
        .willReturn(List.of(ROW, row(otherId, TITLE, ATTACHMENT_ID)));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID), other));
    willThrow(new IllegalStateException("value too long"))
        .given(documentRepository)
        .saveSynced(stored(ATTACHMENT_ID));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).should().saveSynced(other);
    then(syncRepository).should(never()).saveSuccessfulSync(any());
    assertThat(failedFiles()).isEqualTo(1);
  }

  @Test
  void should_skip_a_file_too_large_without_failing_the_sync() {
    // given
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));
    willThrow(new FulcrumAttachmentTooLargeException(ATTACHMENT_ID, DataSize.ofMegabytes(200)))
        .given(importService)
        .importFulcrumDocument(ROW);

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(syncRepository).should().saveSuccessfulSync(SYNCED_AT);
    assertThat(failedFiles()).isZero();
  }

  @Test
  void should_stop_importing_once_the_time_budget_is_used_up() {
    // given
    FulcrumLiteratureRow second = row(UUID.randomUUID(), TITLE, UUID.randomUUID());
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW, second));
    willAnswer(invocation -> clock.advanceBy(TIME_BUDGET.plusSeconds(1)))
        .given(importService)
        .importFulcrumDocument(ROW);

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(importService).should().importFulcrumDocument(ROW);
    then(importService).should(never()).importFulcrumDocument(second);
    then(syncRepository).should(never()).saveSuccessfulSync(any());
    then(lock).should().unlock();
  }

  @Test
  void should_change_nothing_when_fulcrum_cannot_be_queried() {
    // given
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis())
        .willThrow(
            new RestClientResponseException("bad gateway", 502, "Bad Gateway", null, null, null));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).shouldHaveNoInteractions();
    then(importService).shouldHaveNoInteractions();
    then(syncRepository).shouldHaveNoInteractions();
    assertThat(syncs("failed")).isEqualTo(1);
    then(lock).should().unlock();
  }

  @Test
  void should_not_count_a_sync_as_successful_when_it_dies_after_saving_the_metadata() {
    // given
    givenTheLockIsFree();
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID)));
    willThrow(new IllegalStateException("connection lost"))
        .given(documentRepository)
        .markRemovedFromFulcrum(List.of(), SYNCED_AT);

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).should().saveSynced(stored(ATTACHMENT_ID));
    then(syncRepository).should(never()).saveSuccessfulSync(any());
    assertThat(syncs("failed")).isEqualTo(1);
    then(lock).should().unlock();
  }

  @Test
  void should_skip_the_sync_while_fulcrum_is_switched_off() {
    // when
    service(DISABLED).syncUnlessRunning();

    // then
    then(syncLock).shouldHaveNoInteractions();
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_skip_the_sync_while_another_one_runs() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_keep_the_lock_of_a_scheduled_sync_for_at_least_a_minute() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(syncLock).should().tryAcquire(Duration.ofMinutes(1));
  }

  @Test
  void should_run_a_background_sync_and_release_its_lock_right_after() {
    // given
    given(syncLock.tryAcquire(Duration.ZERO)).willReturn(Optional.of(lock));
    given(fulcrumService.findLiteratureMarkedForMonteis()).willReturn(List.of(ROW));

    // when
    service(ENABLED).startSyncInBackground();

    // then
    then(importService).should().importFulcrumDocument(ROW);
    then(lock).should().unlock();
  }

  @Test
  void should_release_the_lock_when_the_executor_rejects_the_background_sync() {
    // given
    givenTheLockIsFree();
    TaskExecutor rejecting =
        task -> {
          throw new TaskRejectedException("queue full");
        };

    // when / then
    assertThrows(
        TaskRejectedException.class, () -> service(ENABLED, rejecting).startSyncInBackground());
    then(lock).should().unlock();
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_background_sync_while_another_one_runs() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when / then
    assertThrows(
        FulcrumSyncAlreadyRunningException.class, () -> service(ENABLED).startSyncInBackground());
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_background_sync_while_fulcrum_is_switched_off() {
    // when / then
    assertThrows(FulcrumDisabledException.class, () -> service(DISABLED).startSyncInBackground());
    then(syncLock).shouldHaveNoInteractions();
  }

  @Test
  void should_derive_the_status_from_the_lock_and_the_last_successful_sync() {
    // given
    given(syncLock.isSyncRunning()).willReturn(true);
    given(syncRepository.findLastSuccessfulSyncAt())
        .willReturn(Optional.of(SYNCED_AT.minusDays(1)));

    // when / then
    assertThat(service(ENABLED).currentStatus())
        .isEqualTo(new FulcrumSyncStatus(true, SYNCED_AT.minusDays(1)));
  }

  @Test
  void should_report_no_successful_sync_before_the_first_one() {
    // given
    given(syncLock.isSyncRunning()).willReturn(false);
    given(syncRepository.findLastSuccessfulSyncAt()).willReturn(Optional.empty());

    // when / then
    assertThat(service(ENABLED).currentStatus()).isEqualTo(new FulcrumSyncStatus(false, null));
  }

  private FulcrumDocumentSyncService service(boolean enabled) {
    return service(enabled, new SyncTaskExecutor());
  }

  private FulcrumDocumentSyncService service(boolean enabled, TaskExecutor taskExecutor) {
    return new FulcrumDocumentSyncService(
        fulcrumService,
        importService,
        documentRepository,
        syncRepository,
        new FulcrumLiteratureMapperImpl(),
        syncLock,
        taskExecutor,
        new FulcrumProperties(
            enabled,
            "https://api.fulcrumapp-eu.com/api",
            "token",
            "Mont Terri Monitoring Systems and Sensors",
            "Mont Terri Literature",
            20000,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            List.of(URI.create("https://attachments.fulcrumapp-eu.com"))),
        new FulcrumDocumentSyncProperties(
            TIME_BUDGET, Duration.ofMinutes(2), DataSize.ofMegabytes(200)),
        clock,
        meterRegistry);
  }

  private void givenTheLockIsFree() {
    given(syncLock.tryAcquire(any())).willReturn(Optional.of(lock));
  }

  private double syncs(String outcome) {
    return meterRegistry
        .counter(FulcrumDocumentSyncService.SYNC_METRIC, "outcome", outcome)
        .count();
  }

  private double failedFiles() {
    return meterRegistry.counter(FulcrumDocumentSyncService.FAILED_FILES_METRIC).count();
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

  // the time budget is checked against the clock before each import, an import moves it on
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
