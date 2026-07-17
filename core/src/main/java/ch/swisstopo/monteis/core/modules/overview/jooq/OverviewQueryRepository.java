package ch.swisstopo.monteis.core.modules.overview.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;

import ch.swisstopo.monteis.core.modules.overview.service.QueryInterface;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
// readOnly=true: required so RlsConnectionProvider's transaction-scoped RLS GUCs actually
// persist for the query (see JooqConfig), and Postgres rejects any write attempted here as
// defense-in-depth. Class-level on purpose since every method here is query-only today — if
// a write method is ever added to this class, give it its own @Transactional (no readOnly)
// explicitly, or it will silently inherit this and fail at the DB.
@Transactional(readOnly = true)
public class OverviewQueryRepository implements QueryInterface {

  private final DSLContext dsl;

  public OverviewQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<ReadSimpleMetricDto> fetchRecentMetrics(int limit) {
    return dsl.selectFrom(SENSOR_READING_SECURED)
        .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
        .limit(limit)
        .fetchInto(ReadSimpleMetricDto.class);
  }
}
