package ch.swisstopo.monteis.core.modules.fulcrumdocument.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FULCRUM_DOCUMENT_SYNC;
import static org.assertj.core.api.Assertions.assertThat;

import ch.swisstopo.monteis.core.itconfig.IT;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IT
class JooqFulcrumSyncRepositoryIT {

  private static final OffsetDateTime FIRST_SYNC =
      OffsetDateTime.of(2026, 10, 14, 2, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime SECOND_SYNC = FIRST_SYNC.plusDays(1);

  @Autowired private JooqFulcrumSyncRepository repository;
  @Autowired private DSLContext dsl;

  @AfterEach
  void removeTheSyncState() {
    dsl.deleteFrom(FULCRUM_DOCUMENT_SYNC).execute();
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
