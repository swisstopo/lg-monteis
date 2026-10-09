package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.modules.experiment.jooq.ExperimentJooqMapper;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.MeasurementStatus;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorDetailQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorDetailParameterResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorDetailResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterReadingResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the sensor detail view with one SQL statement: sensor, main experiment, parameters, formula
 * and type via {@code LEFT JOIN}s, plus the newest reading of each parameter via a {@code LEFT JOIN
 * LATERAL}. A sensor with N parameters yields N rows, one without parameters exactly one row (all
 * {@code SENSOR_PARAMETER} columns null).
 *
 * <p>The lateral subquery is correlated per parameter: one index-friendly "newest row" lookup on
 * the (potentially huge) readings table per parameter instead of ranking every reading. It reads
 * the secured view, so row-level security applies - hence {@code readOnly} transactions.
 */
@Repository
public class JooqSensorDetailQueryRepository implements SensorDetailQueryRepository {

  private static final Table<?> LATEST_READING =
      DSL.lateral(
              DSL.select(
                      SENSOR_READING_SECURED.TIMESTAMP,
                      SENSOR_READING_SECURED.NORM_VALUE,
                      SENSOR_READING_SECURED.RAW_VALUE,
                      SENSOR_READING_SECURED.STATUS)
                  .from(SENSOR_READING_SECURED)
                  .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.eq(SENSOR_PARAMETER.ID))
                  .orderBy(SENSOR_READING_SECURED.TIMESTAMP.desc())
                  .limit(1))
          .as("latest_reading");

  private final DSLContext dsl;
  private final ExperimentJooqMapper experimentJooqMapper;

  public JooqSensorDetailQueryRepository(
      DSLContext dsl, ExperimentJooqMapper experimentJooqMapper) {
    this.dsl = dsl;
    this.experimentJooqMapper = experimentJooqMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SensorDetailResponseDto> findById(UUID id, LocalDate today) {
    Result<Record> rows =
        dsl.select(SENSORS.fields())
            .select(EXPERIMENTS.fields())
            .select(SENSOR_PARAMETER.fields())
            .select(FORMULAS.fields())
            .select(SENSOR_TYPES.fields())
            .select(LATEST_READING.fields())
            .from(SENSORS)
            .leftJoin(EXPERIMENTS)
            .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
            .leftJoin(SENSOR_PARAMETER)
            .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
            .leftJoin(FORMULAS)
            .on(SENSOR_PARAMETER.FORMULA_ID.eq(FORMULAS.ID))
            .leftJoin(SENSOR_TYPES)
            .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID))
            .leftJoin(LATEST_READING)
            .on(DSL.trueCondition())
            .where(SENSORS.ID.eq(id))
            .orderBy(SENSOR_PARAMETER.ID.asc())
            .fetch();

    if (rows.isEmpty()) {
      return Optional.empty();
    }

    List<SensorDetailParameterResponseDto> parameters =
        rows.stream()
            .filter(r -> r.get(SENSOR_PARAMETER.ID) != null)
            .map(JooqSensorDetailQueryRepository::toParameter)
            .toList();

    return Optional.of(toSensor(rows.get(0), parameters, today));
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
        r.get(LATEST_READING.field(SENSOR_READING_SECURED.TIMESTAMP), OffsetDateTime.class);
    if (timestamp == null) {
      return null;
    }
    return new SensorParameterReadingResponseDto(
        r.get(LATEST_READING.field(SENSOR_READING_SECURED.NORM_VALUE)),
        r.get(LATEST_READING.field(SENSOR_READING_SECURED.RAW_VALUE)),
        MeasurementStatus.fromDbValue(
            r.get(LATEST_READING.field(SENSOR_READING_SECURED.STATUS), String.class)),
        timestamp);
  }
}
