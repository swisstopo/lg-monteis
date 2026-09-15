package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.query.SensorParameterRowQueryRepository;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterRowResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.Table;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the sensor grid at (Sensor, SensorParameter) grain via a single {@code LEFT JOIN} chain -
 * a sensor with zero parameters still yields exactly one row (every {@code SENSOR_PARAMETER}
 * column null), a sensor with N parameters yields N rows. {@code totalCount} therefore counts
 * joined rows, not sensors.
 *
 * <p>{@code COLUMNS_BY_COL_ID}, {@code joinedFrom()} and the sort tiebreaker are package-private:
 * also reused by {@code JooqSensorCsvExportQueryRepository}, so a CSV export honors the exact same
 * filter/sort semantics and row grain as the grid.
 */
@Repository
public class JooqSensorParameterRowQueryRepository implements SensorParameterRowQueryRepository {

  static final Map<String, Field<?>> COLUMNS_BY_COL_ID =
      Map.ofEntries(
          Map.entry("name", SENSORS.NAME),
          Map.entry("das", SENSORS.DAS),
          Map.entry("dasSensorAlias", SENSORS.DAS_SENSOR_ALIAS),
          Map.entry("coordinates.x", SENSORS.X),
          Map.entry("coordinates.y", SENSORS.Y),
          Map.entry("coordinates.z", SENSORS.Z),
          Map.entry("active", SENSORS.ACTIVE),
          Map.entry("comment", SENSORS.COMMENT),
          Map.entry("fulcrumId", SENSORS.FULCRUM_ID),
          Map.entry("mainExperiment.name", EXPERIMENTS.NAME),
          Map.entry("parameter.name", SENSOR_PARAMETER.NAME),
          Map.entry("parameter.dasParameterAlias", SENSOR_PARAMETER.DAS_PARAMETER_ALIAS),
          Map.entry("parameter.unit", SENSOR_PARAMETER.UNIT),
          Map.entry("parameter.active", SENSOR_PARAMETER.ACTIVE),
          Map.entry("parameter.comment", SENSOR_PARAMETER.COMMENT),
          Map.entry("parameter.type.name", SENSOR_TYPES.NAME),
          Map.entry("parameter.formula.expression", FORMULAS.EXPRESSION),
          Map.entry("parameter.alarmLimits.lower", SENSOR_PARAMETER.LOWER_ALARM_LIMIT),
          Map.entry("parameter.alarmLimits.upper", SENSOR_PARAMETER.UPPER_ALARM_LIMIT));

  private final DSLContext dsl;

  public JooqSensorParameterRowQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<SensorParameterRowResponseDto> findPaged(PagedRequest request) {
    // Default sort is by sensor name (not id): parameter rows have no meaningful order of their
    // own, and grouping by name keeps a sensor's own rows contiguous - required for the grid's
    // row-spanning to merge its repeated sensor-level cells.
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(request, COLUMNS_BY_COL_ID, SENSORS.NAME.asc());

    List<SensorParameterRowResponseDto> rows =
        dsl.select(SENSORS.fields())
            .select(EXPERIMENTS.fields())
            .select(SENSOR_PARAMETER.fields())
            .select(FORMULAS.fields())
            .select(SENSOR_TYPES.fields())
            .from(joinedFrom())
            .where(criteria.condition())
            .orderBy(withStableTiebreaker(criteria.sortFields()))
            .limit(request.limit())
            .offset(request.offset())
            .fetch(this::toRow);

    int totalCount =
        dsl.fetchCount(dsl.select(SENSORS.ID).from(joinedFrom()).where(criteria.condition()));

    return new PagedResult<>(rows, totalCount);
  }

  // The join chain itself, factored out as a plain Table expression (not a Select) so callers can
  // each .from(joinedFrom()) with their own field list.
  static Table<Record> joinedFrom() {
    return SENSORS
        .leftJoin(EXPERIMENTS)
        .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
        .leftJoin(SENSOR_PARAMETER)
        .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
        .leftJoin(FORMULAS)
        .on(SENSOR_PARAMETER.FORMULA_ID.eq(FORMULAS.ID))
        .leftJoin(SENSOR_TYPES)
        .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID));
  }

  // A sensor's own sort key (or the default, sensor name) only ever groups its rows together if
  // ties are broken consistently - appended after whatever the request asked for, so paging stays
  // stable across requests regardless of which column the grid is currently sorted by.
  static List<SortField<?>> withStableTiebreaker(Collection<SortField<?>> sortFields) {
    List<SortField<?>> withTiebreaker = new ArrayList<>(sortFields);
    withTiebreaker.add(SENSOR_PARAMETER.ID.asc());
    return withTiebreaker;
  }

  private SensorParameterRowResponseDto toRow(Record r) {
    UUID experimentId = r.get(EXPERIMENTS.ID);
    SensorParameterRowResponseDto.MainExperimentRefDto mainExperiment =
        experimentId == null
            ? null
            : new SensorParameterRowResponseDto.MainExperimentRefDto(
                experimentId, r.get(EXPERIMENTS.NAME));

    UUID parameterId = r.get(SENSOR_PARAMETER.ID);
    SensorParameterResponseDto parameter =
        parameterId == null
            ? null
            : new SensorParameterResponseDto(
                parameterId,
                r.get(SENSOR_PARAMETER.NAME),
                r.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS),
                new SensorTypeResponseDto(
                    r.get(SENSOR_TYPES.ID), r.get(SENSOR_TYPES.NAME), r.get(SENSOR_TYPES.VERSION)),
                // jOOQ generates its own Unit enum (bound to the native Postgres enum column),
                // distinct from the domain Unit type SensorParameterResponseDto expects - convert
                // by name, mirroring the implicit conversion MapStruct generates elsewhere
                // (SensorJooqMapper.toParameterDomain).
                Unit.valueOf(r.get(SENSOR_PARAMETER.UNIT).name()),
                new FormulaResponseDto(
                    r.get(FORMULAS.ID), r.get(FORMULAS.EXPRESSION), r.get(FORMULAS.VERSION)),
                new AlarmLimitsDto(
                    r.get(SENSOR_PARAMETER.LOWER_ALARM_LIMIT),
                    r.get(SENSOR_PARAMETER.UPPER_ALARM_LIMIT)),
                r.get(SENSOR_PARAMETER.ACTIVE),
                r.get(SENSOR_PARAMETER.COMMENT),
                r.get(SENSOR_PARAMETER.VERSION));

    return new SensorParameterRowResponseDto(
        r.get(SENSORS.ID),
        r.get(SENSORS.NAME),
        r.get(SENSORS.DAS_SENSOR_ALIAS),
        Das.valueOf(r.get(SENSORS.DAS)),
        r.get(SENSORS.FULCRUM_ID),
        mainExperiment,
        new CoordinatesDto(r.get(SENSORS.X), r.get(SENSORS.Y), r.get(SENSORS.Z)),
        r.get(SENSORS.ACTIVE),
        r.get(SENSORS.COMMENT),
        parameter);
  }
}
