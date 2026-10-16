package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENT_SYNC;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SHEDLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.infrastructure.scheduling.SchedulingConfig;
import ch.swisstopo.monteis.core.itconfig.IT;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// against the real LockProvider, the repository has to read what ShedLock writes. ShedLock commits
// on its own connection, so the rows are removed after each test instead of rolled back
@IT
class JooqFulcrumSyncRepositoryIT {

  private static final OffsetDateTime FIRST_SYNC =
      OffsetDateTime.of(2026, 10, 14, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime SECOND_SYNC = FIRST_SYNC.plusDays(1);

  @Autowired private JooqFulcrumSyncRepository repository;
  @Autowired private LockProvider lockProvider;
  @Autowired private DSLContext dsl;

  @AfterEach
  void removeTheSyncState() {
    dsl.deleteFrom(SHEDLOCK)
        .where(SHEDLOCK.NAME.eq(SchedulingConfig.FULCRUM_DOCUMENT_SYNC_LOCK))
        .execute();
    dsl.deleteFrom(FULCRUM_DOCUMENT_SYNC).execute();
  }

  @Test
  void should_report_a_sync_as_running_until_its_lock_is_released() {
    // given
    SimpleLock lock =
        lockProvider
            .lock(
                new LockConfiguration(
                    Instant.now(),
                    SchedulingConfig.FULCRUM_DOCUMENT_SYNC_LOCK,
                    Duration.ofMinutes(10),
                    Duration.ZERO))
            .orElseThrow();

    // when / then
    try {
      assertThat(repository.isSyncRunning()).isTrue();
    } finally {
      lock.unlock();
    }
    assertThat(repository.isSyncRunning()).isFalse();
  }

  @Test
  void should_not_report_a_sync_while_nobody_took_the_lock() {
    // when / then
    assertThat(repository.isSyncRunning()).isFalse();
  }

  @Test
  void should_not_report_a_sync_whose_pod_died_once_its_lock_expired() {
    // given
    LocalDateTime minuteAgoUtc = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
    dsl.insertInto(SHEDLOCK)
        .set(SHEDLOCK.NAME, SchedulingConfig.FULCRUM_DOCUMENT_SYNC_LOCK)
        .set(SHEDLOCK.LOCK_UNTIL, minuteAgoUtc)
        .set(SHEDLOCK.LOCKED_AT, minuteAgoUtc.minusMinutes(2))
        .set(SHEDLOCK.LOCKED_BY, "core-pod-that-died")
        .execute();

    // when / then
    assertThat(repository.isSyncRunning()).isFalse();
  }

  @Test
  void should_report_no_successful_sync_before_the_first_one() {
    // when / then
    assertThat(repository.findLastSuccessfulSyncAt()).isEmpty();
  }

  @Test
  void should_report_the_start_of_the_last_successful_sync() {
    // given
    repository.saveSuccessfulSync(FIRST_SYNC);

    // when
    repository.saveSuccessfulSync(SECOND_SYNC);

    // then
    assertThat(repository.findLastSuccessfulSyncAt())
        .hasValueSatisfying(lastSync -> assertThat(lastSync).isAtSameInstantAs(SECOND_SYNC));
  }
}
