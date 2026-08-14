package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.Records.mapping;

import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.impl.DSL;
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
  public Optional<ChartDataResponseDto> findMeasurements(
      Long id, OffsetDateTime from, OffsetDateTime to) {

    Record4<Long, String, String, Unit> sensor =
        dsl.select(SENSORS.ID, SENSORS.CODE, SENSORS.NAME, SENSORS.UNIT)
            .from(SENSORS)
            .where(SENSORS.ID.eq(id))
            .fetchOne();

    // Absent when the sensor does not exist, or when RLS hides it from this caller. Both are
    // reported as "not found" so the API never confirms the existence of an invisible sensor.
    if (sensor == null) {
      return Optional.empty();
    }

    List<ChartPointDto> points =
        dsl.select(SENSOR_READING_SECURED.TIMESTAMP, SENSOR_READING_SECURED.NORM_VALUE)
            .from(SENSOR_READING_SECURED)
            // sensor_reading_secured links back to sensors via code, not id
            .where(SENSOR_READING_SECURED.SENSOR_ID.eq(sensor.get(SENSORS.CODE)))
            // INLINE from and to in order to bypass string conversion via fdw
            .and(SENSOR_READING_SECURED.TIMESTAMP.between(DSL.inline(from), DSL.inline(to)))
            .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
            .fetch(mapping(ChartPointDto::new));

    return Optional.of(
        new ChartDataResponseDto(
            sensor.get(SENSORS.ID),
            sensor.get(SENSORS.CODE),
            sensor.get(SENSORS.NAME),
            sensor.get(SENSORS.UNIT),
            points));
  }
}
