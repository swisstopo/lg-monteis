package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_SYNC_RUNS;

import ch.swisstopo.monteis.core.jooq.generated.tables.records.FulcrumSyncRunsRecord;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncResult;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRun;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRunRepository;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqFulcrumSyncRunRepository implements FulcrumSyncRunRepository {

  private final DSLContext dsl;

  public JooqFulcrumSyncRunRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  @Transactional
  public FulcrumSyncRun start(String triggeredBy) {
    dsl.update(FULCRUM_SYNC_RUNS)
        .set(FULCRUM_SYNC_RUNS.STATUS, FulcrumSyncStatus.FAILED.name())
        .set(FULCRUM_SYNC_RUNS.FINISHED_AT, DSL.currentOffsetDateTime())
        .where(FULCRUM_SYNC_RUNS.STATUS.eq(FulcrumSyncStatus.RUNNING.name()))
        .execute();
    return dsl.insertInto(FULCRUM_SYNC_RUNS)
        .set(FULCRUM_SYNC_RUNS.STATUS, FulcrumSyncStatus.RUNNING.name())
        .set(FULCRUM_SYNC_RUNS.TRIGGERED_BY, triggeredBy)
        .returning()
        .fetchSingle(JooqFulcrumSyncRunRepository::toDomain);
  }

  @Override
  @Transactional
  public void finish(UUID runId, FulcrumSyncResult result) {
    dsl.update(FULCRUM_SYNC_RUNS)
        .set(FULCRUM_SYNC_RUNS.STATUS, result.status().name())
        .set(FULCRUM_SYNC_RUNS.FINISHED_AT, DSL.currentOffsetDateTime())
        .set(FULCRUM_SYNC_RUNS.DOCUMENTS_SEEN, result.documentsSeen())
        .set(FULCRUM_SYNC_RUNS.FILES_STORED, result.filesStored())
        .set(FULCRUM_SYNC_RUNS.FILES_FAILED, result.filesFailed())
        .where(FULCRUM_SYNC_RUNS.ID.eq(runId))
        .execute();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FulcrumSyncRun> findLatest() {
    return dsl.selectFrom(FULCRUM_SYNC_RUNS)
        .orderBy(FULCRUM_SYNC_RUNS.STARTED_AT.desc(), FULCRUM_SYNC_RUNS.ID.desc())
        .limit(1)
        .fetchOptional(JooqFulcrumSyncRunRepository::toDomain);
  }

  private static FulcrumSyncRun toDomain(FulcrumSyncRunsRecord runRecord) {
    return new FulcrumSyncRun(
        runRecord.getId(),
        FulcrumSyncStatus.valueOf(runRecord.getStatus()),
        runRecord.getTriggeredBy(),
        runRecord.getStartedAt(),
        runRecord.getFinishedAt(),
        runRecord.getDocumentsSeen(),
        runRecord.getFilesStored(),
        runRecord.getFilesFailed());
  }
}
