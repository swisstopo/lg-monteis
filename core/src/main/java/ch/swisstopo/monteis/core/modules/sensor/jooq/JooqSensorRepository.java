package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FormulasRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorTypesRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorsRecord;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqSensorRepository implements SensorRepository {

  private static final Map<String, Field<?>> COLUMNS_BY_COL_ID =
      Map.of(
          "code", SENSORS.CODE,
          "name", SENSORS.NAME,
          "type.name", SENSOR_TYPES.NAME,
          "unit", SENSORS.UNIT,
          "formula.expression", FORMULAS.EXPRESSION,
          "coordinates.x", SENSORS.X,
          "coordinates.y", SENSORS.Y,
          "coordinates.z", SENSORS.Z,
          "active", SENSORS.ACTIVE,
          "comment", SENSORS.COMMENT);

  private final DSLContext dsl;
  private final SensorJooqMapper mapper;

  public JooqSensorRepository(DSLContext dsl, SensorJooqMapper mapper) {
    this.dsl = dsl;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<Sensor> findPaged(PagedRequest request) {
    // Default to a deterministic order so offset-based paging stays stable across separate
    // requests (Postgres does not guarantee row order without an ORDER BY).
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(request, COLUMNS_BY_COL_ID, SENSORS.ID.asc());

    List<Sensor> data =
        dsl.select(SENSORS.fields())
            .select(FORMULAS.fields())
            .select(SENSOR_TYPES.fields())
            .from(SENSORS)
            .join(FORMULAS)
            .on(SENSORS.FORMULA_ID.eq(FORMULAS.ID))
            .join(SENSOR_TYPES)
            .on(SENSORS.TYPE_ID.eq(SENSOR_TYPES.ID))
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(request.limit())
            .offset(request.offset())
            .fetch(r -> mapper.toDomain(r.into(SENSORS), r.into(FORMULAS), r.into(SENSOR_TYPES)));

    int totalCount =
        dsl.fetchCount(
            dsl.select(SENSORS.ID)
                .from(SENSORS)
                .join(FORMULAS)
                .on(SENSORS.FORMULA_ID.eq(FORMULAS.ID))
                .join(SENSOR_TYPES)
                .on(SENSORS.TYPE_ID.eq(SENSOR_TYPES.ID))
                .where(criteria.condition()));

    return new PagedResult<>(data, totalCount);
  }

  @Override
  @Transactional
  public Sensor create(Sensor sensor) {
    FormulasRecord formulaRecord =
        findOrCreateFormulaByExpression(sensor.getFormula().getExpression());
    SensorTypesRecord typeRecord = findOrCreateSensorTypeByName(sensor.getType().name());
    SensorsRecord createdSensor = mapper.toRecord(sensor);
    dsl.attach(createdSensor);
    createdSensor.setFormulaId(formulaRecord.getId());
    createdSensor.setTypeId(typeRecord.getId());

    try {
      createdSensor.insert();
    } catch (DuplicateKeyException _) {
      throw new FieldBusinessValidationException(
          "code", sensor.getCode(), "validation.unique", Map.of());
    }

    return mapper.toDomain(createdSensor, formulaRecord, typeRecord);
  }

  @Override
  @Transactional(readOnly = true) // required for RLS
  public List<Formula> findAllFormulas() {
    return dsl.selectFrom(FORMULAS)
        .orderBy(FORMULAS.EXPRESSION.asc()) // Clean alphabetical sorting for the UI
        .fetch(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true) // required for RLS
  public List<SensorType> findAllTypes() {
    return dsl.selectFrom(SENSOR_TYPES)
        .orderBy(SENSOR_TYPES.NAME.asc()) // Clean alphabetical sorting for the UI
        .fetch(mapper::toDomain);
  }

  @Override
  @Transactional
  public Sensor update(Sensor sensor) {
    FormulasRecord formulaRecord =
        findOrCreateFormulaByExpression(sensor.getFormula().getExpression());
    SensorTypesRecord typeRecord = findOrCreateSensorTypeByName(sensor.getType().name());
    // fetch existing
    SensorsRecord updatedRecord =
        dsl.selectFrom(SENSORS).where(SENSORS.ID.eq(sensor.getId())).fetchOne();
    if (updatedRecord == null) {
      throw new ObjectBusinessValidationException("object.deleted", Map.of());
    }
    // map new properties to existing
    mapper.updateRecordFromDomain(sensor, updatedRecord);
    updatedRecord.setFormulaId(formulaRecord.getId());
    updatedRecord.setTypeId(typeRecord.getId());
    try {
      updatedRecord.update();
    } catch (DuplicateKeyException _) {
      // unique constraint
      throw new FieldBusinessValidationException(
          "code", sensor.getCode(), "validation.unique", Map.of());
    }

    return mapper.toDomain(updatedRecord, formulaRecord, typeRecord);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Sensor> findById(UUID id) {
    return dsl.select(SENSORS.fields())
        .select(FORMULAS.fields())
        .select(SENSOR_TYPES.fields())
        .from(SENSORS)
        .join(FORMULAS)
        .on(SENSORS.FORMULA_ID.eq(FORMULAS.ID))
        .join(SENSOR_TYPES)
        .on(SENSORS.TYPE_ID.eq(SENSOR_TYPES.ID))
        .where(SENSORS.ID.eq(id))
        .fetchOptional(
            r -> mapper.toDomain(r.into(SENSORS), r.into(FORMULAS), r.into(SENSOR_TYPES)));
  }

  private FormulasRecord findOrCreateFormulaByExpression(String expression) {
    // Attempt to insert. If it already exists, do nothing
    dsl.insertInto(FORMULAS)
        .set(FORMULAS.EXPRESSION, expression)
        .onConflict(FORMULAS.EXPRESSION) // Requires a UNIQUE constraint on the DB column
        .doNothing()
        .execute();

    // Now we can safely fetch it, knowing it definitively exists
    return dsl.selectFrom(FORMULAS).where(FORMULAS.EXPRESSION.eq(expression)).fetchOne();
  }

  private SensorTypesRecord findOrCreateSensorTypeByName(String name) {
    // Attempt to insert. If it already exists, do nothing
    dsl.insertInto(SENSOR_TYPES)
        .set(SENSOR_TYPES.NAME, name)
        .onConflict(SENSOR_TYPES.NAME) // Requires a UNIQUE constraint on the DB column
        .doNothing()
        .execute();

    // Now we can safely fetch it, knowing it definitively exists
    return dsl.selectFrom(SENSOR_TYPES).where(SENSOR_TYPES.NAME.eq(name)).fetchOne();
  }

  @Override
  @Transactional
  public Stream<Sensor> streamUnauditedSensors() {
    return dsl.select(SENSORS.fields())
        .select(FORMULAS.fields())
        .select(SENSOR_TYPES.fields())
        .from(SENSORS)
        .join(FORMULAS)
        .on(SENSORS.FORMULA_ID.eq(FORMULAS.ID))
        .join(SENSOR_TYPES)
        .on(SENSORS.TYPE_ID.eq(SENSOR_TYPES.ID))
        .whereNotExists(
            dsl.selectOne()
                .from(DSL.table("jv_global_id"))
                // JaVers stores IDs as their JSON representation, so a UUID id is stored
                // double-quoted (e.g. "01a2..."). Compare as text instead of casting local_id to
                // uuid, which fails on those surrounding quotes.
                .where(
                    DSL.field("local_id")
                        .eq(
                            DSL.concat(
                                DSL.inline("\""), SENSORS.ID.cast(String.class), DSL.inline("\""))))
                // Ensure this matches your JaVers @TypeName or class name!
                .and(DSL.field("type_name").eq(Sensor.JAVERS_TYPE)))
        .fetchStream()
        .map(
            r -> {
              SensorsRecord sensorsRecord = r.into(SENSORS);
              FormulasRecord formulasRecord = r.into(FORMULAS);
              SensorTypesRecord typesRecord = r.into(SENSOR_TYPES);

              return mapper.toDomain(sensorsRecord, formulasRecord, typesRecord);
            });
  }
}
