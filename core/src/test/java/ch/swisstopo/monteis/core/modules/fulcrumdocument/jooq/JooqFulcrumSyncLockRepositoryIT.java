package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.itconfig.IT;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// against the real LockProvider, the repository has to read what ShedLock writes
@IT
class JooqFulcrumSyncLockRepositoryIT {

  private static final String LOCK_NAME = "fulcrum-sync-lock-it";

  @Autowired private JooqFulcrumSyncLockRepository repository;
  @Autowired private LockProvider lockProvider;

  @Test
  void should_report_a_lock_as_held_until_it_is_released() {
    // given
    SimpleLock lock =
        lockProvider
            .lock(
                new LockConfiguration(
                    Instant.now(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO))
            .orElseThrow();

    // when / then
    assertThat(repository.isHeld(LOCK_NAME)).isTrue();
    lock.unlock();
    assertThat(repository.isHeld(LOCK_NAME)).isFalse();
  }

  @Test
  void should_not_report_a_lock_nobody_ever_took() {
    // when / then
    assertThat(repository.isHeld("never-taken")).isFalse();
  }
}
