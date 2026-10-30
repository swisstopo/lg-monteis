package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDisabledException;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumProperties;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumSyncAlreadyRunningException;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLock;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.HeldSyncLock;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class FulcrumDocumentSyncRunnerTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private static final OffsetDateTime SYNCED_AT = NOW.atOffset(ZoneOffset.UTC);
  private static final Duration TIME_BUDGET = Duration.ofMinutes(8);
  private static final Instant DEADLINE = NOW.plus(TIME_BUDGET);
  private static final boolean ENABLED = true;
  private static final boolean DISABLED = false;

  @Mock private FulcrumDocumentSyncService syncService;
  @Mock private FulcrumSyncLock syncLock;
  @Mock private FulcrumSyncRepository syncRepository;
  @Mock private HeldSyncLock lock;

  @Test
  void should_run_the_sync_and_release_the_lock() {
    // given
    givenTheLockIsFree();

    // when
    runner(ENABLED).syncUnlessRunning();

    // then
    then(syncService).should().sync(DEADLINE);
    then(lock).should().close();
  }

  @Test
  void should_release_the_lock_when_the_sync_crashes() {
    // given
    givenTheLockIsFree();
    willThrow(new IllegalStateException("connection lost")).given(syncService).sync(DEADLINE);

    // when
    runner(ENABLED).syncUnlessRunning();

    // then
    then(lock).should().close();
  }

  @Test
  void should_skip_the_sync_while_fulcrum_is_switched_off() {
    // when
    runner(DISABLED).syncUnlessRunning();

    // then
    then(syncLock).shouldHaveNoInteractions();
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_skip_the_sync_while_another_one_runs() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when
    runner(ENABLED).syncUnlessRunning();

    // then
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_keep_the_lock_of_a_scheduled_sync_for_at_least_a_minute() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());

    // when
    runner(ENABLED).syncUnlessRunning();

    // then
    then(syncLock).should().tryAcquire(Duration.ofMinutes(1));
  }

  @Test
  void should_run_a_background_sync_and_release_its_lock_right_after() {
    // given
    given(syncLock.tryAcquire(Duration.ZERO)).willReturn(Optional.of(lock));

    // when
    runner(ENABLED).startInBackground();

    // then
    then(syncService).should().sync(DEADLINE);
    then(lock).should().close();
  }

  @Test
  void should_release_the_lock_when_the_executor_rejects_the_background_sync() {
    // given
    givenTheLockIsFree();
    TaskExecutor rejecting =
        task -> {
          throw new TaskRejectedException("queue full");
        };
    FulcrumDocumentSyncRunner runner = runner(ENABLED, rejecting);

    // when / then
    assertThrows(TaskRejectedException.class, runner::startInBackground);
    then(lock).should().close();
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_background_sync_while_another_one_runs() {
    // given
    given(syncLock.tryAcquire(any())).willReturn(Optional.empty());
    FulcrumDocumentSyncRunner runner = runner(ENABLED);

    // when / then
    assertThrows(FulcrumSyncAlreadyRunningException.class, runner::startInBackground);
    then(syncService).shouldHaveNoInteractions();
  }

  @Test
  void should_refuse_a_background_sync_while_fulcrum_is_switched_off() {
    // given
    FulcrumDocumentSyncRunner runner = runner(DISABLED);

    // when / then
    assertThrows(FulcrumDisabledException.class, runner::startInBackground);
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
            List.of(URI.create("https://content.fulcrumapp-eu.com"))),
        new FulcrumDocumentSyncProperties(
            TIME_BUDGET, Duration.ofMinutes(2), DataSize.ofMegabytes(200)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void givenTheLockIsFree() {
    given(syncLock.tryAcquire(any())).willReturn(Optional.of(lock));
  }
}
