package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SHEDLOCK;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncLockRepository;
import java.time.LocalDateTime;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqFulcrumSyncLockRepository implements FulcrumSyncLockRepository {

  // ShedLock writes the db time in utc into plain TIMESTAMP columns (usingDbTime)
  private static final Field<LocalDateTime> NOW_UTC =
      DSL.field("timezone('utc', now())", LocalDateTime.class);

  private final DSLContext dsl;

  public JooqFulcrumSyncLockRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  // unlocking sets lock_until to now, a pod that died leaves it until lockAtMostFor runs out
  @Override
  @Transactional(readOnly = true)
  public boolean isHeld(String lockName) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(SHEDLOCK)
            .where(SHEDLOCK.NAME.eq(lockName))
            .and(SHEDLOCK.LOCK_UNTIL.gt(NOW_UTC)));
  }
}
