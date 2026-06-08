package ch.swisstopo.monteis.core.modules.demo.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.RawSimpleMetrics.RAW_SIMPLE_METRICS;

import ch.swisstopo.monteis.core.modules.demo.web.dto.ReadSimpleMetric;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class DemoRepository {

  private final DSLContext dsl;

  public DemoRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<ReadSimpleMetric> fetchRecentMetrics(int limit) {
    return dsl.selectFrom(RAW_SIMPLE_METRICS)
        .orderBy(RAW_SIMPLE_METRICS.TIME.desc())
        .limit(limit)
        .fetch()
        .map(r -> new ReadSimpleMetric(r.getTime(), r.getValue()));
  }
}
