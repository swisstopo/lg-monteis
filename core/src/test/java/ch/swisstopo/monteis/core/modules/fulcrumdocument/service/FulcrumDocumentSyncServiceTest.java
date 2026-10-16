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
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumAttachment;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumDocumentStorage;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntry;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumEntryRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncResult;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRun;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRunRepository;
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
import java.util.List;
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
  private static final OffsetDateTime STARTED_AT = NOW.atOffset(ZoneOffset.UTC);
  private static final UUID RUN_ID = UUID.randomUUID();
  private static final UUID RECORD_ID = UUID.fromString("221ae592-4527-4442-8294-8dc2c5be1168");
  private static final UUID ATTACHMENT_ID = UUID.fromString("22c52651-0b4b-48ca-bf8e-6594e935da0f");
  private static final String FILE_NAME = "Jahns 03 2011 - DS Experiment.pdf";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

  private static final FulcrumLiteratureEntry ENTRY =
      new FulcrumLiteratureEntry(
          RECORD_ID,
          "2011-02",
          "DS Experiment: BDS-1",
          "Jahns E.",
          2011,
          "2 Mont Terri Technical Notes (TN)",
          List.of("Experiment DS", "RACOS"),
          List.of(ATTACHMENT_ID),
          List.of(FILE_NAME));

  @Mock private FulcrumService fulcrumService;
  @Mock private FulcrumAttachments fulcrumAttachments;
  @Mock private FulcrumEntryRepository entryRepository;
  @Mock private FulcrumDocumentRepository documentRepository;
  @Mock private FulcrumSyncRunRepository runRepository;
  @Mock private FulcrumDocumentStorage storage;
  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;
  @Mock private Clock clock;

  @Test
  void should_save_the_entries_as_seen_at_the_start_of_the_run() {
    // given
    givenTheLockIsFree();
    givenARunStartedBy(FulcrumSyncRun.TRIGGERED_BY_SCHEDULE);
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));

    // when
    service("token").syncOnSchedule();

    // then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FulcrumEntry>> saved = ArgumentCaptor.forClass(List.class);
    then(entryRepository).should().saveSeen(saved.capture(), eq(STARTED_AT));
    FulcrumEntry entry = saved.getValue().getFirst();
    assertThat(entry.id()).isEqualTo(RECORD_ID);
    assertThat(entry.number()).isEqualTo("2011-02");
    assertThat(entry.authors()).isEqualTo("Jahns E.");
    assertThat(entry.keywords()).containsExactly("Experiment DS", "RACOS");
    assertThat(entry.attachments())
        .containsExactly(new FulcrumAttachment(ATTACHMENT_ID, RECORD_ID, FILE_NAME));
    InOrder order = inOrder(entryRepository, documentRepository);
    order.verify(entryRepository).markRemovedUnlessSeenAt(STARTED_AT);
    order.verify(documentRepository).reconcileWithEntries();
  }

  @Test
  void should_store_the_content_before_recording_the_document() throws Exception {
    // given
    givenTheLockIsFree();
    givenARunStartedBy(FulcrumSyncRun.TRIGGERED_BY_SCHEDULE);
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(entryRepository.findAttachmentsToStore()).willReturn(List.of(attachment(ATTACHMENT_ID)));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));
    List<byte[]> stored = new ArrayList<>();
    willAnswer(invocation -> stored.add(Files.readAllBytes(invocation.getArgument(2, Path.class))))
        .given(storage)
        .store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));

    // when
    service("token").syncOnSchedule();

    // then
    assertThat(stored).singleElement().isEqualTo(CONTENT);
    InOrder order = inOrder(storage, documentRepository);
    order.verify(storage).store(eq(ATTACHMENT_ID), eq("application/pdf"), any(Path.class));
    order
        .verify(documentRepository)
        .create(
            ATTACHMENT_ID,
            RECORD_ID,
            new DocumentMetadata(FILE_NAME, "application/pdf", CONTENT.length));
    then(runRepository)
        .should()
        .finish(RUN_ID, new FulcrumSyncResult(FulcrumSyncStatus.SUCCEEDED, 1, 1, 0));
    then(lock).should().unlock();
  }

  @Test
  void should_go_on_with_the_other_attachments_when_one_fails() {
    // given
    UUID brokenId = UUID.randomUUID();
    givenTheLockIsFree();
    givenARunStartedBy(FulcrumSyncRun.TRIGGERED_BY_SCHEDULE);
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(entryRepository.findAttachmentsToStore())
        .willReturn(List.of(attachment(brokenId), attachment(ATTACHMENT_ID)));
    given(fulcrumAttachments.open(brokenId)).willThrow(new IllegalStateException("answered 404"));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(CONTENT));

    // when
    service("token").syncOnSchedule();

    // then
    then(documentRepository).should(never()).create(eq(brokenId), any(), any());
    then(documentRepository).should().create(eq(ATTACHMENT_ID), eq(RECORD_ID), any());
    then(runRepository)
        .should()
        .finish(RUN_ID, new FulcrumSyncResult(FulcrumSyncStatus.INCOMPLETE, 1, 1, 1));
  }

  @Test
  void should_not_record_an_empty_download() {
    // given
    givenTheLockIsFree();
    givenARunStartedBy(FulcrumSyncRun.TRIGGERED_BY_SCHEDULE);
    given(clock.instant()).willReturn(NOW);
    given(entryRepository.findAttachmentsToStore()).willReturn(List.of(attachment(ATTACHMENT_ID)));
    given(fulcrumAttachments.open(ATTACHMENT_ID)).willReturn(new ByteArrayInputStream(new byte[0]));

    // when
    service("token").syncOnSchedule();

    // then
    then(storage).shouldHaveNoInteractions();
    then(documentRepository).should(never()).create(any(), any(), any());
    then(runRepository)
        .should()
        .finish(RUN_ID, new FulcrumSyncResult(FulcrumSyncStatus.INCOMPLETE, 0, 0, 1));
  }

  @Test
  void should_stop_storing_documents_once_the_time_budget_is_used_up() {
    // given
    givenTheLockIsFree();
    givenARunStartedBy(FulcrumSyncRun.TRIGGERED_BY_SCHEDULE);
    // lock, deadline, then the check before the one attachment
    given(clock.instant()).willReturn(NOW, NOW, NOW.plus(Duration.ofMinutes(9)));
    given(fulcrumService.getLiteratureToSync()).willReturn(List.of(ENTRY));
    given(entryRepository.findAttachmentsToStore()).willReturn(List.of(attachment(ATTACHMENT_ID)));

    // when
    service("token").syncOnSchedule();

    // then
    then(fulcrumAttachments).shouldHaveNoInteractions();
    then(runRepository)
        .should()
        .finish(RUN_ID, new FulcrumSyncResult(FulcrumSyncStatus.INCOMPLETE, 1, 0, 0));
    then(lock).should().unlock();
  }

  @Test
  void should_fail_the_run_and_keep_the_entries_when_fulcrum_cannot_be_queried() {
    // given
    givenTheLockIsFree();
    givenARunStartedBy(FulcrumSyncRun.TRIGGERED_BY_SCHEDULE);
    given(clock.instant()).willReturn(NOW);
    given(fulcrumService.getLiteratureToSync())
        .willThrow(
            new RestClientResponseException("bad gateway", 502, "Bad Gateway", null, null, null));

    // when
    service("token").syncOnSchedule();

    // then
    then(entryRepository).shouldHaveNoInteractions();
    then(documentRepository).shouldHaveNoInteractions();
    then(runRepository).should().finish(RUN_ID, FulcrumSyncResult.failed());
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
    then(runRepository).shouldHaveNoInteractions();
    then(fulcrumService).shouldHaveNoInteractions();
  }

  @Test
  void should_record_who_started_a_manual_sync() {
    // given
    givenTheLockIsFree();
    givenARunStartedBy("experiment_pi");
    given(clock.instant()).willReturn(NOW);

    // when
    FulcrumSyncRun run = service("token").startSync("experiment_pi");

    // then
    assertThat(run.status()).isEqualTo(FulcrumSyncStatus.RUNNING);
    assertThat(run.triggeredBy()).isEqualTo("experiment_pi");
    then(runRepository)
        .should()
        .finish(RUN_ID, new FulcrumSyncResult(FulcrumSyncStatus.SUCCEEDED, 0, 0, 0));
    then(lock).should().unlock();
  }

  @Test
  void should_refuse_a_manual_sync_while_another_one_runs() {
    // given
    given(clock.instant()).willReturn(NOW);
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    // when / then
    assertThrows(
        FulcrumSyncAlreadyRunningException.class, () -> service("token").startSync("alice"));
    then(runRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_hold_the_lock_at_most_for_the_configured_duration() {
    // given
    givenTheLockIsFree();
    givenARunStartedBy("alice");
    given(clock.instant()).willReturn(NOW);

    // when
    service("token").startSync("alice");

    // then
    ArgumentCaptor<LockConfiguration> configuration =
        ArgumentCaptor.forClass(LockConfiguration.class);
    then(lockProvider).should().lock(configuration.capture());
    assertThat(configuration.getValue().getName()).isEqualTo(FulcrumDocumentSyncService.LOCK_NAME);
    assertThat(configuration.getValue().getLockAtMostUntil())
        .isEqualTo(NOW.plus(Duration.ofMinutes(10)));
  }

  private FulcrumDocumentSyncService service(String apiToken) {
    return new FulcrumDocumentSyncService(
        fulcrumService,
        fulcrumAttachments,
        entryRepository,
        documentRepository,
        runRepository,
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

  private void givenARunStartedBy(String triggeredBy) {
    given(runRepository.start(triggeredBy))
        .willReturn(
            new FulcrumSyncRun(
                RUN_ID,
                FulcrumSyncStatus.RUNNING,
                triggeredBy,
                STARTED_AT,
                null,
                null,
                null,
                null));
  }

  private static FulcrumAttachment attachment(UUID id) {
    return new FulcrumAttachment(id, RECORD_ID, FILE_NAME);
  }
}
