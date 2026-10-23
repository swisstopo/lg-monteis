package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENT_SYNC;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SHEDLOCK;

import ch.swisstopo.monteis.core.infrastructure.scheduling.SchedulingConfig;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqFulcrumSyncRepository implements FulcrumSyncRepository {

  // ShedLock writes utc db time into plain TIMESTAMP (usingDbTime)
  private static final Field<LocalDateTime> NOW_UTC =
      DSL.field("timezone('utc', now())", LocalDateTime.class);

  private final DSLContext dsl;

  public JooqFulcrumSyncRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  // unlock sets lock_until = now, dead pod holds it until lockAtMostFor
  @Override
  @Transactional(readOnly = true)
  public boolean isSyncRunning() {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(SHEDLOCK)
            .where(SHEDLOCK.NAME.eq(SchedulingConfig.FULCRUM_DOCUMENT_SYNC_LOCK))
            .and(SHEDLOCK.LOCK_UNTIL.gt(NOW_UTC)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OffsetDateTime> findLastSuccessfulSyncAt() {
    return dsl.select(FULCRUM_DOCUMENT_SYNC.LAST_SUCCESSFUL_SYNC_AT)
        .from(FULCRUM_DOCUMENT_SYNC)
        .fetchOptional(Record1::value1);
  }

  @Override
  @Transactional
  public void saveSuccessfulSync(OffsetDateTime startedAt) {
    dsl.insertInto(FULCRUM_DOCUMENT_SYNC)
        .set(FULCRUM_DOCUMENT_SYNC.LAST_SUCCESSFUL_SYNC_AT, startedAt)
        .onConflict(FULCRUM_DOCUMENT_SYNC.ID)
        .doUpdate()
        .set(FULCRUM_DOCUMENT_SYNC.LAST_SUCCESSFUL_SYNC_AT, startedAt)
        .execute();
  }
}
