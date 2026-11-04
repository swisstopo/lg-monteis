package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SHEDLOCK;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumDocumentSyncProperties;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLock;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.HeldSyncLock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShedLockFulcrumSyncLock implements FulcrumSyncLock {

  static final String LOCK_NAME = "fulcrum-document-sync";

  // ShedLock writes utc db time into plain TIMESTAMP (usingDbTime)
  private static final Field<LocalDateTime> NOW_UTC =
      DSL.field("timezone('utc', now())", LocalDateTime.class);

  private final LockProvider lockProvider;
  private final DSLContext dsl;
  private final FulcrumDocumentSyncProperties syncProperties;
  private final Clock clock;

  public ShedLockFulcrumSyncLock(
      LockProvider lockProvider,
      DSLContext dsl,
      FulcrumDocumentSyncProperties syncProperties,
      Clock clock) {
    this.lockProvider = lockProvider;
    this.dsl = dsl;
    this.syncProperties = syncProperties;
    this.clock = clock;
  }

  @Override
  public Optional<HeldSyncLock> tryAcquire(Duration keptAtLeastFor) {
    return lockProvider
        .lock(
            new LockConfiguration(
                clock.instant(), LOCK_NAME, syncProperties.lockAtMostFor(), keptAtLeastFor))
        .map(lock -> lock::unlock);
  }

  // ShedLock can only take a lock, not tell if held. unlock sets lock_until = now, dead pod holds
  // it until lockAtMostFor
  @Override
  @Transactional(readOnly = true)
  public boolean isSyncRunning() {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(SHEDLOCK)
            .where(SHEDLOCK.NAME.eq(LOCK_NAME))
            .and(SHEDLOCK.LOCK_UNTIL.gt(NOW_UTC)));
  }
}
