package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.Records.mapping;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.select;

import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MeasurementQueryRepository implements MeasurementQuery {

  private final DSLContext dsl;

  public MeasurementQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<ChartDataResponseDto> findMeasurements(
      List<Long> ids, OffsetDateTime from, OffsetDateTime to) {
    return dsl.select(
            SENSORS.ID,
            SENSORS.CODE,
            SENSORS.NAME,
            SENSORS.UNIT,
            multiset(
                    select(SENSOR_READING_SECURED.TIMESTAMP, SENSOR_READING_SECURED.NORM_VALUE)
                        .from(SENSOR_READING_SECURED)
                        // sensor_reading_secured links back to sensors via code, not id
                        .where(SENSOR_READING_SECURED.SENSOR_ID.eq(SENSORS.CODE))
                        .and(SENSOR_READING_SECURED.TIMESTAMP.between(from, to))
                        .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc()))
                .convertFrom(r -> r.map(mapping(ChartPointDto::new))))
        .from(SENSORS)
        .where(SENSORS.ID.in(ids))
        .fetch(mapping(ChartDataResponseDto::new));
  }
}
