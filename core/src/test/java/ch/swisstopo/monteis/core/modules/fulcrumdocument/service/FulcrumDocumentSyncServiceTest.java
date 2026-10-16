package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import ch.swisstopo.monteis.core.infrastructure.document.DocumentMetadata;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumAttachments;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureEntry;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumService;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocument;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryDetails;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLockRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentSyncServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private static final OffsetDateTime SEEN_AT = NOW.atOffset(ZoneOffset.UTC);
  private static final UUID RECORD_ID = UUID.fromString("221ae592-4527-4442-8294-8dc2c5be1168");
  private static final UUID ATTACHMENT_ID = UUID.fromString("22c52651-0b4b-48ca-bf8e-6594e935da0f");
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

  private static final FulcrumLiteratureEntry ENTRY = entry(RECORD_ID, ATTACHMENT_ID);
  private static final FulcrumEntryDetails DETAILS = detailsOf(RECORD_ID);

  @Mock private FulcrumService fulcrumService;
  @Mock private FulcrumAttachments fulcrumAttachments;
  @Mock private FulcrumEntryRepository entryRepository;
  @Mock private FulcrumSyncLockRepository lockRepository;
  @Mock private FulcrumDocumentStorage storage;
  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;
  @Mock private Clock clock;

  @Test
  void should_update_the_details_of_known_entries_without_downloading_again() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(entryRepository.findDocumentIdsByEntry()).willReturn(Map.of(RECORD_ID, ATTACHMENT_ID));

    // when
    service("token").syncOnSchedule();

    // then
    then(entryRepository).should().saveSeenDetails(List.of(DETAILS), SEEN_AT);
    then(entryRepository).should().markRemovedUnlessSeenAt(SEEN_AT);
    then(fulcrumAttachments).shouldHaveNoInteractions();
    then(entryRepository).should(never()).saveWithDocument(any(), any(), any());
    then(lock).should().unlock();
  }

  @Test
  void should_store_the_document_of_a_new_entry_before_saving_the_entry() throws Exception {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));
    List<byte[]> stored = new ArrayList<>();
    willAnswer(invocation -> stored.add(Files.readAllBytes(invocation.getArgument(2, Path.class))))
        .given(storage)
        .store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));

    // when
    service("token").syncOnSchedule();

    // then
    assertThat(stored).singleElement().isEqualTo(CONTENT);
    InOrder order = inOrder(storage, entryRepository);
    order.verify(storage).store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));
    order
        .verify(entryRepository)
        .saveWithDocument(
            DETAILS,
            new FulcrumDocument(
                ATTACHMENT_ID, new DocumentMetadata(FILE_NAME, "application/pdf", CONTENT.length)),
            SEEN_AT);
    then(entryRepository).should().saveSeenDetails(List.of(), SEEN_AT);
  }

  @Test
  void should_switch_an_entry_to_its_replaced_file() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(entryRepository.findDocumentIdsByEntry())
        .willReturn(Map.of(RECORD_ID, UUID.randomUUID()));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));

    // when
    service("token").syncOnSchedule();

    // then the details count as seen right away, in case the new download fails
    then(entryRepository).should().saveSeenDetails(List.of(DETAILS), SEEN_AT);
    then(storage).should().store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));
    then(entryRepository).should().saveWithDocument(eq(DETAILS), any(), eq(SEEN_AT));
  }

  @Test
  void should_take_the_first_file_of_an_entry_with_several() {
    // given
    UUID second = UUID.randomUUID();
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync())
        .willReturn(List.of(entry(RECORD_ID, ATTACHMENT_ID, second)));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));

    // when
    service("token").syncOnSchedule();

    // then
    then(fulcrumAttachments).should(never()).open(second);
    then(entryRepository).should().saveWithDocument(eq(DETAILS), any(), eq(SEEN_AT));
  }

  @Test
  void should_go_on_with_the_other_entries_when_one_download_fails() {
    // given
    UUID brokenRecord = UUID.randomUUID();
    UUID brokenAttachment = UUID.randomUUID();
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync())
        .willReturn(List.of(entry(brokenRecord, brokenAttachment), ENTRY));
    given(fulcrumAttachments.open(brokenAttachment))
        .willThrow(new IllegalStateException("answered 404"));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));

    // when
    service("token").syncOnSchedule();

    // then
    then(entryRepository).should().saveWithDocument(eq(DETAILS), any(), eq(SEEN_AT));
    then(entryRepository)
        .should(never())
        .saveWithDocument(eq(detailsOf(brokenRecord)), any(), any());
  }

  @Test
  void should_not_save_an_entry_with_an_empty_file() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(new byte[0]));

    // when
    service("token").syncOnSchedule();

    // then
    then(storage).shouldHaveNoInteractions();
    then(entryRepository).should(never()).saveWithDocument(any(), any(), any());
  }

  @Test
  void should_stop_downloading_once_the_time_budget_is_used_up() {
    // given
    givenTheLockIsFree();
    // lock, start of the sync, then the check before the one download
    given(clock.instant()).willReturn(NOW, NOW, NOW.plus(Duration.ofMinutes(9)));
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));

    // when
    service("token").syncOnSchedule();

    // then
    then(fulcrumAttachments).shouldHaveNoInteractions();
    then(entryRepository).should().markRemovedUnlessSeenAt(SEEN_AT);
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
    service("token").syncOnSchedule();

    // then
    then(entryRepository).shouldHaveNoInteractions();
    then(lock).should().unlock();
  }

  @Test
  void should_skip_the_scheduled_sync_without_an_api_token() {
    // when
    service("").syncOnSchedule();

    // then
    then(lockProvider).shouldHaveNoInteractions();
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_skip_the_scheduled_sync_while_another_one_runs() {
    // given
    given(clock.instant()).willReturn(NOW);
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    // when
    service("token").syncOnSchedule();

    // then
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_report_a_started_sync_as_running() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);
    given(entryRepository.findLastSeenAt()).willReturn(Optional.of(SEEN_AT.minusDays(1)));

    // when
    FulcrumSyncStatus status = service("token").startSync();

    // then
    assertThat(status).isEqualTo(new FulcrumSyncStatus(true, SEEN_AT.minusDays(1)));
    then(lock).should().unlock();
  }

  @Test
  void should_refuse_a_manual_sync_while_another_one_runs() {
    // given
    given(clock.instant()).willReturn(NOW);
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    // when / then
    assertThrows(FulcrumSyncAlreadyRunningException.class, () -> service("token").startSync());
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_hold_the_lock_at_most_for_the_configured_duration() {
    // given
    givenTheLockIsFree();
    given(clock.instant()).willReturn(NOW);

    // when
    service("token").startSync();

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
    given(entryRepository.findLastSeenAt()).willReturn(Optional.empty());

    // when / then
    assertThat(service("token").getStatus()).isEqualTo(new FulcrumSyncStatus(false, null));
  }

  private FulcrumDocumentSyncService service(String apiToken) {
    return new FulcrumDocumentSyncService(
        fulcrumService,
        fulcrumAttachments,
        entryRepository,
        lockRepository,
        storage,
        lockProvider,
        new SyncTaskExecutor(),
        new FulcrumProperties(
            "https://api.fulcrumapp-eu.com/api",
            apiToken,
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

  private static FulcrumEntryDetails detailsOf(UUID recordId) {
    return new FulcrumEntryDetails(
        recordId,
        "2011-02",
        "DS Experiment: BDS-1",
        "Jahns E.",
        2011,
        "2 Mont Terri Technical Notes (TN)",
        List.of("Experiment DS", "RACOS"));
  }
}
