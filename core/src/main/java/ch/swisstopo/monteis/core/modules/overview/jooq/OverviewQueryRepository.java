package ch.swisstopo.monteis.core.modules.overview.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.Records.mapping;

import ch.swisstopo.monteis.core.modules.overview.query.QueryInterface;
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

  @Override
  public List<ReadSimpleMetricDto> fetchRecentMetrics(int limit) {
    return dsl.select(
            SENSOR_READING_SECURED.TIMESTAMP,
            SENSOR_READING_SECURED.SENSOR_ID,
            SENSOR_READING_SECURED.RAW_VALUE,
            SENSOR_READING_SECURED.NORM_VALUE,
            SENSOR_READING_SECURED.VERSION,
            SENSOR_READING_SECURED.STATUS)
        .from(SENSOR_READING_SECURED)
        .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
        .limit(limit)
        .fetch(mapping(ReadSimpleMetricDto::new));
  }
}
