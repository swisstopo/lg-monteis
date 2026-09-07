package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
// import static
// ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;

import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  public Optional<ChartDataResponseDto> findMeasurements(
      UUID id, OffsetDateTime from, OffsetDateTime to) {

    var parameterInfo =
        dsl.select(SENSOR_PARAMETER.ID, SENSOR_PARAMETER.NAME, SENSOR_PARAMETER.UNIT, SENSORS.NAME)
            .from(SENSOR_PARAMETER)
            .join(SENSORS)
            .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
            .where(SENSOR_PARAMETER.ID.eq(id))
            .fetchOne();

    if (parameterInfo == null) {
      return Optional.empty();
    }

    List<ChartPointDto> points = List.of();
    //                dsl.select(SENSOR_READING_SECURED.TIMESTAMP,
    // SENSOR_READING_SECURED.NORM_VALUE)
    //                        .from(SENSOR_READING_SECURED)
    //                        // sensor_reading_secured should now link via the parameter ID
    //
    // .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(parameterInfo.get(SENSOR_PARAMETER.ID)))
    //                        // INLINE from and to in order to bypass string conversion via fdw
    //                        .and(SENSOR_READING_SECURED.TIMESTAMP.between(DSL.inline(from),
    // DSL.inline(to)))
    //                        .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
    //                        .fetch(mapping(ChartPointDto::new));

    String combinedName =
        parameterInfo.get(SENSORS.NAME) + " - " + parameterInfo.get(SENSOR_PARAMETER.NAME);

    //        return Optional.of(
    //                new ChartDataResponseDto(
    //                        parameterInfo.get(SENSOR_PARAMETER.ID),
    //                        parameterInfo.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS),
    //                        combinedName,
    //                        parameterInfo.get(Unit.CANDELA),
    //                        points));
    return null;
  }
}
