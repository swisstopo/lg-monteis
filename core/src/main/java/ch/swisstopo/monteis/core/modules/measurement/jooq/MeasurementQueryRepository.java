package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.Records.mapping;
import static org.jooq.impl.DSL.*;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.jooq.ExperimentJooqMapper;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.query.MeasurementQuery;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.ChartDataResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.MeasurementResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorDetailParameterResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorDetailResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorParameterReadingResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MeasurementQueryRepository implements MeasurementQuery {

  private final DSLContext dsl;
  private final Clock clock;
  private final ExperimentJooqMapper experimentJooqMapper;

  // Correlated per sensor_parameter, joined once below and reused by both the projection and the
  // sort/filter map so "dasKey" resolves against the exact same lateral alias either way. Also
  // backs findSensorDetail. Reads the secured view, so row-level security applies.
  private static final Table<?> LATEST_READINGS =
      lateral(
              select(
                      SENSOR_READING_SECURED.TIMESTAMP,
                      SENSOR_READING_SECURED.NORM_VALUE,
                      SENSOR_READING_SECURED.RAW_VALUE,
                      SENSOR_READING_SECURED.DAS_KEY,
                      SENSOR_READING_SECURED.STATUS)
                  .from(SENSOR_READING_SECURED)
                  .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(SENSOR_PARAMETER.ID))
                  .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
                  .limit(1))
          .as("latest_readings");

  // Prefers the das_key actually stored on the latest reading - readings keep the key that was
  // valid when they were ingested even if the sensor/parameter alias is later remapped (see
  // db/meta/schema/V14) - falling back to composing it from current metadata (mirrors
  // ch.swisstopo.monteis.contracts.DasKey.compose) for a parameter with no readings yet.
  private static final Field<String> DAS_KEY =
      DSL.coalesce(
              LATEST_READINGS.field(SENSOR_READING_SECURED.DAS_KEY),
              DSL.concat(
                  SENSORS.DAS,
                  DSL.inline("__"),
                  SENSORS.DAS_SENSOR_ALIAS,
                  DSL.inline("__"),
                  SENSOR_PARAMETER.DAS_PARAMETER_ALIAS))
          .as("das_key");

  private static final Map<String, Field<?>> MEASUREMENT_COLUMNS_BY_COL_ID =
      Map.ofEntries(
          Map.entry("sensorParameterId", SENSOR_PARAMETER.ID),
          Map.entry("dasKey", DAS_KEY),
          Map.entry("experimentName", EXPERIMENTS.NAME),
          Map.entry("sensorParameterName", SENSOR_PARAMETER.NAME),
          Map.entry("sensorName", SENSORS.NAME),
          Map.entry(
              "newestMeasurement",
              Objects.requireNonNull(LATEST_READINGS.field(SENSOR_READING_SECURED.TIMESTAMP))),
          Map.entry(
              "measureValue",
              Objects.requireNonNull(LATEST_READINGS.field(SENSOR_READING_SECURED.NORM_VALUE))),
          Map.entry("unit", SENSOR_PARAMETER.UNIT),
          Map.entry("sensorType", SENSOR_TYPES.NAME),
          Map.entry("coordinates.x", SENSORS.X),
          Map.entry("coordinates.y", SENSORS.Y),
          Map.entry("coordinates.z", SENSORS.Z),
          Map.entry("alarmLimits.lower", SENSOR_PARAMETER.LOWER_ALARM_LIMIT),
          Map.entry("alarmLimits.upper", SENSOR_PARAMETER.UPPER_ALARM_LIMIT),
          Map.entry("active", SENSORS.ACTIVE),
          Map.entry("comment", SENSORS.COMMENT));

  public MeasurementQueryRepository(
      DSLContext dsl, Clock clock, ExperimentJooqMapper experimentJooqMapper) {
    this.dsl = dsl;
    this.clock = clock;
    this.experimentJooqMapper = experimentJooqMapper;
  }

  @Override
  public Optional<SensorDetailResponseDto> findSensorDetail(UUID id, LocalDate today) {
    Result<Record> rows =
        dsl.select(SENSORS.fields())
            .select(EXPERIMENTS.fields())
            .select(SENSOR_PARAMETER.fields())
            .select(FORMULAS.fields())
            .select(SENSOR_TYPES.fields())
            .select(LATEST_READINGS.fields())
            .from(SENSORS)
            .leftJoin(EXPERIMENTS)
            .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
            .leftJoin(SENSOR_PARAMETER)
            .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
            .leftJoin(FORMULAS)
            .on(SENSOR_PARAMETER.FORMULA_ID.eq(FORMULAS.ID))
            .leftJoin(SENSOR_TYPES)
            .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID))
            .leftJoin(LATEST_READINGS)
            .on(trueCondition())
            .where(SENSORS.ID.eq(id))
            .orderBy(SENSOR_PARAMETER.ID.asc())
            .fetch();

    if (rows.isEmpty()) {
      return Optional.empty();
    }

    List<SensorDetailParameterResponseDto> parameters =
        rows.stream()
            .filter(r -> r.get(SENSOR_PARAMETER.ID) != null)
            .map(MeasurementQueryRepository::toParameter)
            .toList();

    return Optional.of(toSensor(rows.get(0), parameters, today));
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
            // sensor_reading_secured links via sensor_parameter.id /
            // sensor_reading.sensor_parameter_id
            // (both UUID, globally unique) - see db/meta/schema/V13.
            .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(DSL.inline(id)))
            // INLINE from and to in order to bypass string conversion via fdw
            .and(SENSOR_READING_SECURED.TIMESTAMP.between(DSL.inline(from), DSL.inline(to)))
            .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
            .fetch(mapping(ChartPointDto::new));

    String combinedName =
        parameterInfo.get(SENSORS.NAME) + " - " + parameterInfo.get(SENSOR_PARAMETER.NAME);

    String dasKey =
        dsl.select(SENSOR_READING_SECURED.DAS_KEY)
            .from(SENSOR_READING_SECURED)
            .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(DSL.inline(id)))
            .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
            .limit(1)
            .fetchOptional(SENSOR_READING_SECURED.DAS_KEY)
            .orElseGet(() -> parameterInfo.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS));

    return Optional.of(
        new ChartDataResponseDto(
            parameterInfo.get(SENSOR_PARAMETER.ID),
            dasKey,
            combinedName,
            Unit.valueOf(parameterInfo.get(SENSOR_PARAMETER.UNIT).toString()),
            points));
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<MeasurementResponseDto> findPaged(PagedRequest request) {

    var criteria =
        PagedRequestJooqTranslator.translate(
            request, MEASUREMENT_COLUMNS_BY_COL_ID, SENSOR_PARAMETER.SENSOR_ID.asc());

    var baseTable =
        SENSORS
            .join(SENSOR_PARAMETER)
            .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
            .leftJoin(EXPERIMENTS)
            .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
            .leftJoin(SENSOR_TYPES)
            .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID));

    var fullTable = baseTable.leftJoin(LATEST_READINGS).on(trueCondition());

    List<MeasurementRow> rows =
        dsl.select(
                SENSOR_PARAMETER.ID,
                DAS_KEY,
                EXPERIMENTS.NAME,
                SENSORS.NAME,
                SENSOR_PARAMETER.NAME,
                LATEST_READINGS.field(SENSOR_READING_SECURED.TIMESTAMP),
                LATEST_READINGS.field(SENSOR_READING_SECURED.NORM_VALUE),
                LATEST_READINGS.field(SENSOR_READING_SECURED.STATUS),
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
            .fetch(mapping(MeasurementRow::new));

    Map<UUID, List<ChartPointDto>> trendByParamId = fetchTrends(rows);

    List<MeasurementResponseDto> data =
        rows.stream()
            .map(
                row ->
                    new MeasurementResponseDto(
                        row.sensorParameterId(),
                        row.dasKey(),
                        row.experimentName(),
                        row.sensorName(),
                        row.sensorParameterName(),
                        row.newestMeasurement(),
                        row.measureValue(),
                        row.unit().name(),
                        row.sensorType(),
                        new CoordinatesDto(row.x(), row.y(), row.z()),
                        new AlarmLimitsDto(row.alarmLimitFrom(), row.alarmLimitTo()),
                        row.active(),
                        row.comment(),
                        trendByParamId.getOrDefault(row.sensorParameterId(), List.of()),
                        MeasurementStatus.fromDbValue(row.measurementStatus())))
            .toList();

    boolean needsReadings =
        request.filterModel() != null
            && (request.filterModel().containsKey("measureValue")
                || request.filterModel().containsKey("newestMeasurement")
                // dasKey now resolves against latest_readings too (see DAS_KEY above)
                || request.filterModel().containsKey("dasKey"));

    int totalCount =
        dsl.fetchCount(
            dsl.select(SENSORS.ID)
                .from(needsReadings ? fullTable : baseTable)
                .where(criteria.condition()));

    return new PagedResult<>(data, totalCount);
  }

  private SensorDetailResponseDto toSensor(
      Record r, List<SensorDetailParameterResponseDto> parameters, LocalDate today) {
    return new SensorDetailResponseDto(
        r.get(SENSORS.ID),
        r.get(SENSORS.NAME),
        r.get(SENSORS.DAS_SENSOR_ALIAS),
        Das.valueOf(r.get(SENSORS.DAS)),
        r.get(SENSORS.FULCRUM_ID),
        toExperiment(r, today),
        toCoordinates(r),
        r.get(SENSORS.ACTIVE),
        r.get(SENSORS.COMMENT),
        r.get(SENSORS.VERSION),
        parameters);
  }

  private ExperimentResponseDto toExperiment(Record r, LocalDate today) {
    if (r.get(EXPERIMENTS.ID) == null) {
      return null;
    }
    Experiment experiment = experimentJooqMapper.toDomain(r.into(EXPERIMENTS));
    return new ExperimentResponseDto(
        experiment.getId(),
        experiment.getName(),
        experiment.getComment(),
        new PeriodDto(experiment.getPeriod().start(), experiment.getPeriod().end()),
        experiment.getStatus(today),
        experiment.getVersion(),
        experiment.getSensorCount());
  }

  private static CoordinatesDto toCoordinates(Record r) {
    Double x = r.get(SENSORS.X);
    Double y = r.get(SENSORS.Y);
    Double z = r.get(SENSORS.Z);
    return x == null || y == null || z == null ? null : new CoordinatesDto(x, y, z);
  }

  private static SensorDetailParameterResponseDto toParameter(Record r) {
    return new SensorDetailParameterResponseDto(
        r.get(SENSOR_PARAMETER.ID),
        r.get(SENSOR_PARAMETER.NAME),
        r.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS),
        new SensorTypeResponseDto(
            r.get(SENSOR_TYPES.ID), r.get(SENSOR_TYPES.NAME), r.get(SENSOR_TYPES.VERSION)),
        Unit.valueOf(r.get(SENSOR_PARAMETER.UNIT).name()),
        new FormulaResponseDto(
            r.get(FORMULAS.ID), r.get(FORMULAS.EXPRESSION), r.get(FORMULAS.VERSION)),
        new AlarmLimitsDto(
            r.get(SENSOR_PARAMETER.LOWER_ALARM_LIMIT), r.get(SENSOR_PARAMETER.UPPER_ALARM_LIMIT)),
        r.get(SENSOR_PARAMETER.ACTIVE),
        r.get(SENSOR_PARAMETER.COMMENT),
        r.get(SENSOR_PARAMETER.VERSION),
        toReading(r));
  }

  private static SensorParameterReadingResponseDto toReading(Record r) {
    OffsetDateTime timestamp =
        r.get(LATEST_READINGS.field(SENSOR_READING_SECURED.TIMESTAMP), OffsetDateTime.class);
    if (timestamp == null) {
      return null;
    }
    return new SensorParameterReadingResponseDto(
        r.get(LATEST_READINGS.field(SENSOR_READING_SECURED.NORM_VALUE)),
        r.get(LATEST_READINGS.field(SENSOR_READING_SECURED.RAW_VALUE)),
        MeasurementStatus.fromDbValue(
            r.get(LATEST_READINGS.field(SENSOR_READING_SECURED.STATUS), String.class)),
        timestamp);
  }

  // The per-row correlated "trend" subquery used to re-run this range scan once per page row
  // across the FDW link into TimescaleDB (N round trips). Since trend is display-only - it never
  // participates in sort/filter/pagination, unlike latest_readings above - it can be split out
  // into a single batched range scan for exactly this page's parameter ids instead.
  private Map<UUID, List<ChartPointDto>> fetchTrends(List<MeasurementRow> rows) {
    if (rows.isEmpty()) {
      return Map.of();
    }

    // INLINE the ids for the same reason as trendFrom below: as bind values they reach
    // timescaledb as remote parameters, which keeps postgres_fdw from pushing this IN list into
    // the foreign scan.
    List<Field<UUID>> parameterIds =
        rows.stream().map(MeasurementRow::sensorParameterId).<Field<UUID>>map(DSL::inline).toList();
    OffsetDateTime trendFrom = OffsetDateTime.now(clock).minusDays(4);

    return dsl.select(
            SENSOR_READING_SECURED.SENSOR_PARAMETER_ID,
            SENSOR_READING_SECURED.TIMESTAMP,
            SENSOR_READING_SECURED.NORM_VALUE)
        .from(SENSOR_READING_SECURED)
        .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.in(parameterIds))
        // INLINE trendFrom in order to bypass string conversion via fdw, so the
        // bound is pushed down to timescaledb instead of pulling every reading
        // across the fdw and filtering locally.
        .and(SENSOR_READING_SECURED.TIMESTAMP.ge(DSL.inline(trendFrom)))
        .orderBy(SENSOR_READING_SECURED.TIMESTAMP.asc())
        .fetchGroups(
            SENSOR_READING_SECURED.SENSOR_PARAMETER_ID,
            r ->
                new ChartPointDto(
                    r.get(SENSOR_READING_SECURED.TIMESTAMP),
                    r.get(SENSOR_READING_SECURED.NORM_VALUE)));
  }

  // Mirrors MeasurementResponseDto minus "trend", which is fetched and merged separately by
  // fetchTrends() (see findPaged above).
  private record MeasurementRow(
      UUID sensorParameterId,
      String dasKey,
      String experimentName,
      String sensorName,
      String sensorParameterName,
      OffsetDateTime newestMeasurement,
      Double measureValue,
      String measurementStatus,
      ch.swisstopo.monteis.core.jooq.generated.enums.Unit unit,
      String sensorType,
      Double x,
      Double y,
      Double z,
      Double alarmLimitFrom,
      Double alarmLimitTo,
      Boolean active,
      String comment) {}
}
