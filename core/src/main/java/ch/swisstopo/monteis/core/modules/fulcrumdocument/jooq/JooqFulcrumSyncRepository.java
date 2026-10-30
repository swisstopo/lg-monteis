package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENT_SYNC;

import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqFulcrumSyncRepository implements FulcrumSyncRepository {

  private final DSLContext dsl;

  public JooqFulcrumSyncRepository(DSLContext dsl) {
    this.dsl = dsl;
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
