package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_SYNC_RUNS;
import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncResult;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncRun;
import ch.swisstopo.monteis.core.modules.fulcrumdocument.domain.FulcrumSyncStatus;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IT
@Transactional
class JooqFulcrumSyncRunRepositoryIT {

  @Autowired private JooqFulcrumSyncRunRepository repository;
  @Autowired private DSLContext dsl;

  @Test
  void should_start_a_running_run_and_report_it_as_the_latest() {
    // when
    FulcrumSyncRun run = repository.start("experiment_pi");

    // then
    assertThat(run.status()).isEqualTo(FulcrumSyncStatus.RUNNING);
    assertThat(run.triggeredBy()).isEqualTo("experiment_pi");
    assertThat(run.startedAt()).isNotNull();
    assertThat(run.finishedAt()).isNull();
    assertThat(repository.findLatest()).contains(run);
  }

  @Test
  void should_close_a_run_left_running_by_a_dead_pod_as_failed() {
    // given
    FulcrumSyncRun abandoned = repository.start(FulcrumSyncRun.TRIGGERED_BY_SCHEDULE);

    // when
    FulcrumSyncRun run = repository.start("alice");

    // then
    assertThat(repository.findLatest()).contains(run);
    assertThat(
            dsl.select(FULCRUM_SYNC_RUNS.STATUS, FULCRUM_SYNC_RUNS.FINISHED_AT)
                .from(FULCRUM_SYNC_RUNS)
                .where(FULCRUM_SYNC_RUNS.ID.eq(abandoned.id()))
                .fetchSingle())
        .satisfies(
            row -> {
              assertThat(row.value1()).isEqualTo(FulcrumSyncStatus.FAILED.name());
              assertThat(row.value2()).isNotNull();
            });
  }

  @Test
  void should_record_the_outcome_of_a_run() {
    // given
    FulcrumSyncRun run = repository.start("alice");

    // when
    repository.finish(run.id(), new FulcrumSyncResult(FulcrumSyncStatus.INCOMPLETE, 650, 12, 3));

    // then
    FulcrumSyncRun finished = repository.findLatest().orElseThrow();
    assertThat(finished.status()).isEqualTo(FulcrumSyncStatus.INCOMPLETE);
    assertThat(finished.finishedAt()).isNotNull();
    assertThat(finished.documentsSeen()).isEqualTo(650);
    assertThat(finished.filesStored()).isEqualTo(12);
    assertThat(finished.filesFailed()).isEqualTo(3);
  }
}
