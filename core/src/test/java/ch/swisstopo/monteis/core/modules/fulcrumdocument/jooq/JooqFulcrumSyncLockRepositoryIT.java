package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLockRepository;
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

  @Autowired private JooqFulcrumSyncLockRepository repository;
  @Autowired private LockProvider lockProvider;

  @Test
  void should_report_a_sync_as_running_until_its_lock_is_released() {
    // given
    SimpleLock lock =
        lockProvider
            .lock(
                new LockConfiguration(
                    Instant.now(),
                    FulcrumSyncLockRepository.LOCK_NAME,
                    Duration.ofMinutes(10),
                    Duration.ZERO))
            .orElseThrow();

    // when / then
    assertThat(repository.isSyncRunning()).isTrue();
    lock.unlock();
    assertThat(repository.isSyncRunning()).isFalse();
  }

  @Test
  void should_not_report_a_sync_while_nobody_took_the_lock() {
    // when / then
    assertThat(repository.isSyncRunning()).isFalse();
  }
}
