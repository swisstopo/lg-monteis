package ch.swisstopo.monteis.core.modules.overview.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.RawSensorReading.RAW_SENSOR_READING;

import ch.swisstopo.monteis.core.modules.overview.service.QueryInterface;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class OverviewRepository implements QueryInterface {

  private final DSLContext dsl;

  public OverviewRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<ReadSimpleMetricDto> fetchRecentMetrics(int limit) {
    return dsl.selectFrom(RAW_SENSOR_READING)
        .orderBy(RAW_SENSOR_READING.TIMESTAMP.desc())
        .limit(limit)
        .fetchInto(ReadSimpleMetricDto.class);
  }
}
