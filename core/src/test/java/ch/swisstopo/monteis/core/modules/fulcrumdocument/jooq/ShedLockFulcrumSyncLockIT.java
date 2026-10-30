package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SHEDLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLock;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.HeldSyncLock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// ShedLock commits on own connection, so rows deleted after each test, no rollback
@IT
class ShedLockFulcrumSyncLockIT {

  @Autowired private FulcrumSyncLock syncLock;
  @Autowired private DSLContext dsl;

  @AfterEach
  void removeTheLock() {
    dsl.deleteFrom(SHEDLOCK).where(SHEDLOCK.NAME.eq(ShedLockFulcrumSyncLock.LOCK_NAME)).execute();
  }

  @Test
  void should_report_a_sync_as_running_until_its_lock_is_released() {
    // given
    HeldSyncLock lock = syncLock.tryAcquire(Duration.ZERO).orElseThrow();

    // when / then
    try (lock) {
      assertThat(syncLock.isSyncRunning()).isTrue();
    }
    assertThat(syncLock.isSyncRunning()).isFalse();
  }

  @Test
  void should_refuse_the_lock_while_another_sync_holds_it() {
    // given
    try (HeldSyncLock _ = syncLock.tryAcquire(Duration.ZERO).orElseThrow()) {

      // when / then
      assertThat(syncLock.tryAcquire(Duration.ZERO)).isEmpty();
    }
  }

  @Test
  void should_hand_out_the_lock_again_once_released() {
    // given
    syncLock.tryAcquire(Duration.ZERO).orElseThrow().close();

    // when / then
    try (HeldSyncLock _ = syncLock.tryAcquire(Duration.ZERO).orElseThrow()) {
      assertThat(syncLock.isSyncRunning()).isTrue();
    }
  }

  @Test
  void should_keep_a_released_lock_for_as_long_as_it_was_kept_at_least_for() {
    // given
    syncLock.tryAcquire(Duration.ofMinutes(1)).orElseThrow().close();

    // when / then
    assertThat(syncLock.isSyncRunning()).isTrue();
    assertThat(syncLock.tryAcquire(Duration.ZERO)).isEmpty();
  }

  @Test
  void should_not_report_a_sync_while_nobody_took_the_lock() {
    // when / then
    assertThat(syncLock.isSyncRunning()).isFalse();
  }

  @Test
  void should_not_report_a_sync_whose_pod_died_once_its_lock_expired() {
    // given
    LocalDateTime minuteAgoUtc = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
    dsl.insertInto(SHEDLOCK)
        .set(SHEDLOCK.NAME, ShedLockFulcrumSyncLock.LOCK_NAME)
        .set(SHEDLOCK.LOCK_UNTIL, minuteAgoUtc)
        .set(SHEDLOCK.LOCKED_AT, minuteAgoUtc.minusMinutes(2))
        .set(SHEDLOCK.LOCKED_BY, "core-pod-that-died")
        .execute();

    // when / then
    assertThat(syncLock.isSyncRunning()).isFalse();
    syncLock.tryAcquire(Duration.ZERO).orElseThrow().close();
  }
}
