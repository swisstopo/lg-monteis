package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDisabledException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentSyncService.ImportCounts;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.service.FulcrumDocumentSyncService.SyncOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentSyncRunnerTest {

  private static final OffsetDateTime SYNCED_AT = OffsetDateTime.parse("2026-10-15T00:00:00Z");
  private static final boolean ENABLED = true;
  private static final boolean DISABLED = false;
  private static final SyncOutcome SUCCESSFUL = new SyncOutcome(1, 0, 0, imports(1, 0));
  private static final SyncOutcome TWO_DOCUMENTS_FAILED = new SyncOutcome(3, 1, 0, imports(1, 1));

  @Mock private FulcrumDocumentSyncService syncService;
  @Mock private FulcrumSyncLock syncLock;
  @Mock private FulcrumSyncRepository syncRepository;
  @Mock private SimpleLock lock;

  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void should_run_the_sync_and_release_the_lock() {
    // given
    givenTheLockIsFree();
    given(syncService.sync()).willReturn(SUCCESSFUL);

    // when
    runner(ENABLED).runUnlessRunning();

    // then
    then(syncService).should().sync();
    assertThat(syncs("success")).isEqualTo(1);
    then(lock).should().unlock();
  }

  @Test
  void should_count_a_sync_with_failed_documents_as_failed() {
    // given
    givenTheLockIsFree();
    given(syncService.sync()).willReturn(TWO_DOCUMENTS_FAILED);

    // when
    runner(ENABLED).runUnlessRunning();

    // then
    assertThat(syncs("failed")).isEqualTo(1);
    assertThat(failedFiles()).isEqualTo(2);
  }

  @Test
  void should_count_a_sync_that_crashed_as_failed_and_release_the_lock() {
    // given
    givenTheLockIsFree();
    given(syncService.sync()).willThrow(new IllegalStateException("connection lost"));

    // when
    runner(ENABLED).runUnlessRunning();

    // then
    assertThat(syncs("failed")).isEqualTo(1);
    then(lock).should().unlock();
  }

  @Test
  void should_skip_the_sync_while_fulcrum_is_switched_off() {
    // when
    runner(DISABLED).runUnlessRunning();

    // then
    then(syncLock).shouldHaveNoInteractions();
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_skip_the_sync_while_another_one_runs() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when
    runner(ENABLED).runUnlessRunning();

    // then
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_keep_the_lock_of_a_scheduled_sync_for_at_least_a_minute() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when
    runner(ENABLED).runUnlessRunning();

    // then
    then(syncLock).should().tryAcquire(Duration.ofMinutes(1));
  }

  @Test
  void should_run_a_background_sync_and_release_its_lock_right_after() {
    // given
    given(syncLock.tryAcquire(Duration.ZERO)).willReturn(Optional.of(lock));
    given(syncService.sync()).willReturn(SUCCESSFUL);

    // when
    runner(ENABLED).startInBackground();

    // then
    then(syncService).should().sync();
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
    assertThrows(TaskRejectedException.class, () -> runner(ENABLED, rejecting).startInBackground());
    then(lock).should().unlock();
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_background_sync_while_another_one_runs() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when / then
    assertThrows(
        FulcrumSyncAlreadyRunningException.class, () -> runner(ENABLED).startInBackground());
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_background_sync_while_fulcrum_is_switched_off() {
    // when / then
    assertThrows(FulcrumDisabledException.class, () -> runner(DISABLED).startInBackground());
    then(syncLock).shouldHaveNoInteractions();
  }

  @Test
  void should_derive_the_status_from_the_lock_and_the_last_successful_sync() {
    // given
    given(syncLock.isSyncRunning()).willReturn(true);
    given(syncRepository.findLastSuccessfulSyncAt()).willReturn(Optional.of(SYNCED_AT));

    // when / then
    assertThat(runner(ENABLED).currentStatus()).isEqualTo(new FulcrumSyncStatus(true, SYNCED_AT));
  }

  @Test
  void should_report_no_successful_sync_before_the_first_one() {
    // given
    given(syncLock.isSyncRunning()).willReturn(false);
    given(syncRepository.findLastSuccessfulSyncAt()).willReturn(Optional.empty());

    // when / then
    assertThat(runner(ENABLED).currentStatus()).isEqualTo(new FulcrumSyncStatus(false, null));
  }

  private FulcrumDocumentSyncRunner runner(boolean enabled) {
    return runner(enabled, new SyncTaskExecutor());
  }

  private FulcrumDocumentSyncRunner runner(boolean enabled, TaskExecutor taskExecutor) {
    return new FulcrumDocumentSyncRunner(
        syncService,
        syncLock,
        syncRepository,
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
        meterRegistry);
  }

  private void givenTheLockIsFree() {
    given(syncLock.tryAcquire(any())).willReturn(Optional.of(lock));
  }

  private double syncs(String outcome) {
    return meterRegistry.counter(FulcrumDocumentSyncRunner.SYNC_METRIC, "outcome", outcome).count();
  }

  private double failedFiles() {
    return meterRegistry.counter(FulcrumDocumentSyncRunner.FAILED_FILES_METRIC).count();
  }

  private static ImportCounts imports(int imported, int failed) {
    return new ImportCounts(imported, 0, failed, false);
  }
}
