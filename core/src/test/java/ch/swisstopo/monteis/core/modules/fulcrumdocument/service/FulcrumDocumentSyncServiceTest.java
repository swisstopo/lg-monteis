package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDisabledException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry.FulcrumAttachmentReference;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentFile;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLockRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentSyncServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private static final OffsetDateTime SEEN_AT = NOW.atOffset(ZoneOffset.UTC);
  private static final boolean ENABLED = true;
  private static final boolean DISABLED = false;
  private static final UUID RECORD_ID = UUID.fromString("221ae592-4527-4442-8294-8dc2c5be1168");
  private static final UUID ATTACHMENT_ID = UUID.fromString("22c52651-0b4b-48ca-bf8e-6594e935da0f");
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final FulcrumLiteratureEntry ENTRY = entry(RECORD_ID, ATTACHMENT_ID);
  private static final FulcrumAttachmentReference ATTACHMENT =
      new FulcrumAttachmentReference(ATTACHMENT_ID, FILE_NAME);

  @Mock private FulcrumService fulcrumService;
  @Mock private FulcrumDocumentImportService importService;
  @Mock private FulcrumDocumentRepository documentRepository;
  @Mock private FulcrumSyncLockRepository lockRepository;
  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;
  @Mock private Clock clock;

  @Test
  void should_refresh_stored_documents_without_importing_their_file_again() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(documentRepository.findAll()).willReturn(List.of(stored(ATTACHMENT_ID)));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).should().saveSeen(List.of(stored(ATTACHMENT_ID)), SEEN_AT);
    then(documentRepository).should().markRemovedUnlessSeenAt(SEEN_AT);
    then(importService).shouldHaveNoInteractions();
    then(lock).should().unlock();
  }

  @Test
  void should_import_the_file_of_a_new_document() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).should().saveSeen(List.of(), SEEN_AT);
    then(importService).should().importQuietly(ENTRY, ATTACHMENT, SEEN_AT);
  }

  @Test
  void should_import_a_replaced_file_and_count_its_document_as_seen_right_away() {
    // given
    UUID oldAttachment = UUID.randomUUID();
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(documentRepository.findAll()).willReturn(List.of(stored(oldAttachment)));

    // when
    service(ENABLED).syncUnlessRunning();

    // then the document keeps its old file until the new one is imported
    then(documentRepository).should().saveSeen(List.of(stored(oldAttachment)), SEEN_AT);
    then(importService).should().importQuietly(ENTRY, ATTACHMENT, SEEN_AT);
  }

  @Test
  void should_take_the_first_file_of_a_document_with_several() {
    // given
    FulcrumLiteratureEntry withTwoFiles = entry(RECORD_ID, ATTACHMENT_ID, UUID.randomUUID());
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(withTwoFiles));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(importService).should().importQuietly(withTwoFiles, ATTACHMENT, SEEN_AT);
  }

  @Test
  void should_go_on_with_the_other_documents_when_one_import_fails() {
    // given
    FulcrumLiteratureEntry broken = entry(UUID.randomUUID(), UUID.randomUUID());
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(broken, ENTRY));
    given(importService.importQuietly(eq(broken), any(), eq(SEEN_AT))).willReturn(false);

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(importService).should().importQuietly(ENTRY, ATTACHMENT, SEEN_AT);
  }

  @Test
  void should_stop_importing_once_the_time_budget_is_used_up() {
    // given
    givenTheLockIsFree();
    // lock, start of the sync, then the check before the one import
    given(clock.instant()).willReturn(NOW, NOW, NOW.plus(Duration.ofMinutes(9)));
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(importService).shouldHaveNoInteractions();
    then(documentRepository).should().markRemovedUnlessSeenAt(SEEN_AT);
    then(lock).should().unlock();
  }

  @Test
  void should_change_nothing_when_fulcrum_cannot_be_queried() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync())
        .willThrow(
            new RestClientResponseException("bad gateway", 502, "Bad Gateway", null, null, null));

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(documentRepository).shouldHaveNoInteractions();
    then(importService).shouldHaveNoInteractions();
    then(lock).should().unlock();
  }

  @Test
  void should_skip_the_sync_while_fulcrum_is_switched_off() {
    // when
    service(DISABLED).syncUnlessRunning();

    // then
    then(lockProvider).shouldHaveNoInteractions();
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_skip_the_sync_while_another_one_runs() {
    // given
    given(clock.instant()).willReturn(NOW);
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    // when
    service(ENABLED).syncUnlessRunning();

    // then
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_report_a_sync_started_in_the_background_as_running() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(documentRepository.findLastSeenAt()).willReturn(Optional.of(SEEN_AT.minusDays(1)));

    // when
    FulcrumSyncStatus status = service(ENABLED).startSyncInBackground();

    // then
    assertThat(status).isEqualTo(new FulcrumSyncStatus(true, SEEN_AT.minusDays(1)));
    then(lock).should().unlock();
  }

  @Test
  void should_refuse_a_background_sync_while_another_one_runs() {
    // given
    given(clock.instant()).willReturn(NOW);
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    // when / then
    assertThrows(
        FulcrumSyncAlreadyRunningException.class, () -> service(ENABLED).startSyncInBackground());
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_background_sync_while_fulcrum_is_switched_off() {
    // when / then
    assertThrows(FulcrumDisabledException.class, () -> service(DISABLED).startSyncInBackground());
    then(lockProvider).shouldHaveNoInteractions();
  }

  @Test
  void should_hold_the_lock_at_most_for_the_configured_duration() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);

    // when
    service(ENABLED).startSyncInBackground();

    // then
    ArgumentCaptor<LockConfiguration> configuration =
        ArgumentCaptor.forClass(LockConfiguration.class);
    then(lockProvider).should().lock(configuration.capture());
    assertThat(configuration.getValue().getName()).isEqualTo(FulcrumDocumentSyncService.LOCK_NAME);
    assertThat(configuration.getValue().getLockAtMostUntil())
        .isEqualTo(NOW.plus(Duration.ofMinutes(10)));
  }

  @Test
  void should_derive_the_status_from_the_lock_and_the_last_seen_entry() {
    // given
    given(lockRepository.isHeld(FulcrumDocumentSyncService.LOCK_NAME)).willReturn(false);
    given(documentRepository.findLastSeenAt()).willReturn(Optional.empty());

    // when / then
    assertThat(service(ENABLED).currentStatus()).isEqualTo(new FulcrumSyncStatus(false, null));
  }

  private FulcrumDocumentSyncService service(boolean enabled) {
    return new FulcrumDocumentSyncService(
        fulcrumService,
        importService,
        documentRepository,
        new FulcrumLiteratureMapperImpl(),
        lockRepository,
        lockProvider,
        new SyncTaskExecutor(),
        new FulcrumProperties(
            enabled,
            "https://api.fulcrumapp-eu.com/api",
            "token",
            "Mont Terri Monitoring Systems and Sensors",
            "Mont Terri Literature",
            20000,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60)),
        new FulcrumDocumentSyncProperties(Duration.ofMinutes(8), Duration.ofMinutes(10)),
        clock);
  }

  private void givenTheLockIsFree() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
  }

  private static FulcrumLiteratureEntry entry(UUID recordId, UUID... attachmentIds) {
    return new FulcrumLiteratureEntry(
        recordId,
        "2011-02",
        "DS Experiment: BDS-1",
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"),
        List.of(attachmentIds),
        Arrays.stream(attachmentIds).map(id -> FILE_NAME).toList());
  }

  private static FulcrumDocument stored(UUID attachmentId) {
    return new FulcrumDocument(
        RECORD_ID,
        "2011-02",
        "DS Experiment: BDS-1",
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"),
        new FulcrumDocumentFile(
            attachmentId, new DocumentMetadata(FILE_NAME, "application/pdf", 8)),
        null);
  }
}
