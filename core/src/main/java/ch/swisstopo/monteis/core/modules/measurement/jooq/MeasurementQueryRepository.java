package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;

import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MeasurementQueryRepository implements MeasurementQuery {
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private final DSLContext dsl;

  public MeasurementQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<ChartDataResponseDto> findMeasurements(
      List<Long> ids, OffsetDateTime from, OffsetDateTime to) {
    long fetchStart = System.nanoTime();
    List<ChartDataResponseDto> result =
        dsl.select(SENSORS.ID, SENSORS.CODE, SENSORS.NAME, SENSORS.UNIT)
            .from(SENSORS)
            .where(SENSORS.ID.in(ids))
            .fetch(
                sensor -> {
                  List<ChartPointDto> points =
                      dsl.select(
                              SENSOR_READING_SECURED.TIMESTAMP, SENSOR_READING_SECURED.NORM_VALUE)
                          .from(SENSOR_READING_SECURED)
                          .where(SENSOR_READING_SECURED.SENSOR_ID.eq(sensor.get(SENSORS.CODE)))
                          .and(SENSOR_READING_SECURED.TIMESTAMP.between(from, to))
                          .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
                          .fetch(r -> new ChartPointDto(r.value1(), r.value2()));
                  return new ChartDataResponseDto(
                      sensor.get(SENSORS.ID),
                      sensor.get(SENSORS.CODE),
                      sensor.get(SENSORS.NAME),
                      sensor.get(SENSORS.UNIT),
                      points);
                });

    log.info("database fetch took {} ms", (System.nanoTime() - fetchStart) / 1_000_000);
    return result;
  }
}
