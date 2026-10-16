package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.infrastructure.scheduling.SchedulingConfig;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class FulcrumSyncLockTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

  @Mock private LockProvider lockProvider;
  @Mock private FulcrumSyncRepository syncRepository;
  @Mock private SimpleLock lock;

  @Test
  void should_take_the_fulcrum_document_sync_lock_for_the_configured_durations() {
    // given
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    // when
    Optional<SimpleLock> acquired = syncLock().tryAcquire(Duration.ofMinutes(1));

    // then
    assertThat(acquired).contains(lock);
    ArgumentCaptor<LockConfiguration> configuration =
        ArgumentCaptor.forClass(LockConfiguration.class);
    then(lockProvider).should().lock(configuration.capture());
    assertThat(configuration.getValue().getName())
        .isEqualTo(SchedulingConfig.FULCRUM_DOCUMENT_SYNC_LOCK);
    assertThat(configuration.getValue().getLockAtMostUntil())
        .isEqualTo(NOW.plus(Duration.ofMinutes(2)));
    assertThat(configuration.getValue().getLockAtLeastUntil())
        .isEqualTo(NOW.plus(Duration.ofMinutes(1)));
  }

  @Test
  void should_report_nothing_acquired_while_another_pod_holds_the_lock() {
    // given
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    // when / then
    assertThat(syncLock().tryAcquire(Duration.ZERO)).isEmpty();
  }

  @Test
  void should_report_a_sync_running_while_the_lock_is_held() {
    // given
    given(syncRepository.isSyncRunning()).willReturn(true);

    // when / then
    assertThat(syncLock().isSyncRunning()).isTrue();
  }

  private FulcrumSyncLock syncLock() {
    return new FulcrumSyncLock(
        lockProvider,
        syncRepository,
        new FulcrumDocumentSyncProperties(
            Duration.ofMinutes(8), Duration.ofMinutes(2), DataSize.ofMegabytes(200)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }
}
