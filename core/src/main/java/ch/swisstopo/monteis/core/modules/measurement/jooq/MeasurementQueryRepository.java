package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.Records.mapping;
import static org.jooq.impl.DSL.*;

import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.MeasurementResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MeasurementQueryRepository implements MeasurementQuery {

  private final DSLContext dsl;

  private static final Map<String, Field<?>> MEASUREMENT_COLUMNS_BY_COL_ID =
      Map.ofEntries(
          Map.entry("sensorId", SENSORS.ID),
          Map.entry("dasSensorAlias", SENSORS.DAS_SENSOR_ALIAS),
          Map.entry("experimentName", EXPERIMENTS.NAME),
          Map.entry("sensorName", SENSORS.NAME),
          Map.entry("unit", SENSOR_PARAMETER.UNIT),
          Map.entry("sensorType", SENSOR_TYPES.NAME),
          Map.entry("x", SENSORS.X),
          Map.entry("y", SENSORS.Y),
          Map.entry("z", SENSORS.Z),
          Map.entry("alarmLimitFrom", SENSOR_PARAMETER.LOWER_ALARM_LIMIT),
          Map.entry("alarmLimitTo", SENSOR_PARAMETER.UPPER_ALARM_LIMIT),
          Map.entry("active", SENSORS.ACTIVE),
          Map.entry("comment", SENSORS.COMMENT));

  public MeasurementQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<ChartDataResponseDto> findChartData(
      UUID id, OffsetDateTime from, OffsetDateTime to) {

    var parameterInfo =
        dsl.select(
                SENSOR_PARAMETER.ID,
                SENSOR_PARAMETER.NAME,
                SENSOR_PARAMETER.DAS_PARAMETER_ALIAS,
                SENSOR_PARAMETER.UNIT,
                SENSORS.NAME)
            .from(SENSOR_PARAMETER)
            .join(SENSORS)
            .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
            .where(SENSOR_PARAMETER.ID.eq(id))
            .fetchOne();

    if (parameterInfo == null) {
      return Optional.empty();
    }

    List<ChartPointDto> points =
        dsl.select(SENSOR_READING_SECURED.TIMESTAMP, SENSOR_READING_SECURED.NORM_VALUE)
            .from(SENSOR_READING_SECURED)
            // sensor_reading_secured now links via sensor_parameter.das_parameter_alias
            .where(
                SENSOR_READING_SECURED.SENSOR_ID.eq(
                    parameterInfo.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS)))
            // INLINE from and to in order to bypass string conversion via fdw
            .and(SENSOR_READING_SECURED.TIMESTAMP.between(DSL.inline(from), DSL.inline(to)))
            .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
            .fetch(mapping(ChartPointDto::new));

    String combinedName =
        parameterInfo.get(SENSORS.NAME) + " - " + parameterInfo.get(SENSOR_PARAMETER.NAME);

    return Optional.of(
        new ChartDataResponseDto(
            parameterInfo.get(SENSOR_PARAMETER.ID),
            parameterInfo.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS),
            combinedName,
            Unit.valueOf(parameterInfo.get(SENSOR_PARAMETER.UNIT).toString()),
            points));
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<MeasurementResponseDto> findPaged(PagedRequest request) {

    var criteria =
        PagedRequestJooqTranslator.translate(
            request, MEASUREMENT_COLUMNS_BY_COL_ID, SENSORS.ID.asc());

    var latestReadings =
        lateral(
                dsl.select(SENSOR_READING_SECURED.TIMESTAMP, SENSOR_READING_SECURED.NORM_VALUE)
                    .from(SENSOR_READING_SECURED)
                    .where(
                        SENSOR_READING_SECURED.SENSOR_ID.eq(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS))
                    .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
                    .limit(1))
            .as("latest_readings");

    var baseTable =
        SENSORS
            .join(SENSOR_PARAMETER)
            .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
            .leftJoin(EXPERIMENTS)
            .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
            .leftJoin(SENSOR_TYPES)
            .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID));

    var fullTable = baseTable.leftJoin(latestReadings).on(trueCondition());

    var data =
        dsl.select(
                SENSORS.ID,
                SENSORS.DAS_SENSOR_ALIAS,
                EXPERIMENTS.NAME,
                SENSORS.NAME,
                latestReadings.field(SENSOR_READING_SECURED.TIMESTAMP),
                latestReadings.field(SENSOR_READING_SECURED.NORM_VALUE),
                SENSOR_PARAMETER.UNIT,
                SENSOR_TYPES.NAME,
                SENSORS.X,
                SENSORS.Y,
                SENSORS.Z,
                SENSOR_PARAMETER.LOWER_ALARM_LIMIT,
                SENSOR_PARAMETER.UPPER_ALARM_LIMIT,
                SENSORS.ACTIVE,
                SENSORS.COMMENT)
            .from(fullTable)
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(request.limit())
            .offset(request.offset())
            .fetchInto(MeasurementResponseDto.class);

    boolean needsReadings =
        request.filterModel() != null
            && (request.filterModel().containsKey("measureValue")
                || request.filterModel().containsKey("newestMeasurement"));

    int totalCount =
        dsl.fetchCount(
            dsl.select(SENSORS.ID)
                .from(needsReadings ? fullTable : baseTable)
                .where(criteria.condition()));

    return new PagedResult<>(data, totalCount);
  }
}
