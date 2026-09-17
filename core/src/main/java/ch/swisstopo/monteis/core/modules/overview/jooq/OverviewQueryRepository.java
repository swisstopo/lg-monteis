package ch.swisstopo.monteis.core.modules.overview.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.Records.mapping;

import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.overview.query.QueryInterface;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record7;
import org.jooq.SelectOnConditionStep;
import org.jooq.SortField;
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

  /** Maps the measurements table's ag-grid colIds to the fields they sort/filter on. */
  private static final Map<String, Field<?>> COLUMNS_BY_COL_ID =
      Map.of(
          "timestamp", SENSOR_READING_SECURED.TIMESTAMP,
          "dasKey", SENSOR_READING_SECURED.DAS_KEY,
          "rawValue", SENSOR_READING_SECURED.RAW_VALUE,
          "normValue", SENSOR_READING_SECURED.NORM_VALUE);

  private final DSLContext dsl;

  public OverviewQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<ReadSimpleMetricDto> fetchRecentMetrics(int limit) {
    return metrics()
        .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
        .limit(limit)
        .fetch(mapping(ReadSimpleMetricDto::new));
  }

  @Override
  public PagedResult<ReadSimpleMetricDto> findPagedMetrics(PagedRequest request) {
    // Newest first by default, the order fetchRecentMetrics used and the table showed before it
    // paged.
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            request, COLUMNS_BY_COL_ID, SENSOR_READING_SECURED.TIMESTAMP.desc());

    List<ReadSimpleMetricDto> rows =
        metrics()
            .where(criteria.condition())
            .orderBy(totalOrder(criteria.sortFields()))
            .limit(request.limit())
            .offset(request.offset())
            .fetch(mapping(ReadSimpleMetricDto::new));

    int totalCount =
        dsl.fetchCount(
            dsl.select(SENSOR_READING_SECURED.TIMESTAMP)
                .from(SENSOR_READING_SECURED)
                .join(SENSOR_PARAMETER)
                .on(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(SENSOR_PARAMETER.ID))
                .where(criteria.condition()));

    return new PagedResult<>(rows, totalCount);
  }

  /**
   * Appends the readings' primary key (timestamp, das_key) to the requested sort so the ORDER BY
   * is always total. Readings tie on timestamp by design - every sensor reporting in the same tick
   * shares one - and offset-based paging repeats or skips rows whose sort keys tie.
   */
  private Collection<SortField<?>> totalOrder(Collection<SortField<?>> sortFields) {
    List<SortField<?>> ordered = new ArrayList<>(sortFields);
    ordered.add(SENSOR_READING_SECURED.TIMESTAMP.desc());
    ordered.add(SENSOR_READING_SECURED.DAS_KEY.asc());
    return ordered;
  }

  private SelectOnConditionStep<
          Record7<OffsetDateTime, String, Double, Double, Short, String, UUID>>
      metrics() {
    return dsl.select(
            SENSOR_READING_SECURED.TIMESTAMP,
            SENSOR_READING_SECURED.DAS_KEY,
            SENSOR_READING_SECURED.RAW_VALUE,
            SENSOR_READING_SECURED.NORM_VALUE,
            SENSOR_READING_SECURED.VERSION,
            SENSOR_READING_SECURED.STATUS,
            SENSOR_PARAMETER.ID)
        .from(SENSOR_READING_SECURED)
        .join(SENSOR_PARAMETER)
        .on(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(SENSOR_PARAMETER.ID));
  }
}
