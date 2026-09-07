package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;

import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentsRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.FormulasRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorParameterRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorTypesRecord;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.SensorsRecord;
import ch.swisstopo.monteis.core.modules.experiment.jooq.ExperimentJooqMapper;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorParameter;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqSensorRepository implements SensorRepository {

  // Updated to match the new columns on the sensors table
  private static final Map<String, Field<?>> COLUMNS_BY_COL_ID =
      Map.ofEntries(
          Map.entry("name", SENSORS.NAME),
          Map.entry("dasSensorAlias", SENSORS.DAS_SENSOR_ALIAS),
          Map.entry("coordinates.x", SENSORS.X),
          Map.entry("coordinates.y", SENSORS.Y),
          Map.entry("coordinates.z", SENSORS.Z),
          Map.entry("active", SENSORS.ACTIVE),
          Map.entry("comment", SENSORS.COMMENT),
          Map.entry("fulcrumId", SENSORS.FULCRUM_ID),
          Map.entry("mainExperiment.name", EXPERIMENTS.NAME));

  private final DSLContext dsl;
  private final SensorJooqMapper mapper;
  private final ExperimentJooqMapper experimentMapper;

  public JooqSensorRepository(
      DSLContext dsl, SensorJooqMapper mapper, ExperimentJooqMapper experimentMapper) {
    this.dsl = dsl;
    this.mapper = mapper;
    this.experimentMapper = experimentMapper;
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
            .select(EXPERIMENTS.fields())
            .from(SENSORS)
            .leftJoin(EXPERIMENTS)
            .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(request.limit())
            .offset(request.offset())
            .fetch(
                r -> {
                  Sensor sensor = mapper.toDomain(r.into(SENSORS));
                  ExperimentsRecord expRecord = r.into(EXPERIMENTS);
                  if (expRecord.getId() != null) {
                    sensor.setMainExperiment(experimentMapper.toDomain(expRecord));
                  }
                  return sensor;
                });

    if (!data.isEmpty()) {
      List<UUID> sensorIds = data.stream().map(Sensor::getId).toList();
      Map<UUID, List<SensorParameter>> paramsMap =
          dsl.select(SENSOR_PARAMETER.fields())
              .select(FORMULAS.fields())
              .select(SENSOR_TYPES.fields())
              .from(SENSOR_PARAMETER)
              .join(FORMULAS)
              .on(SENSOR_PARAMETER.FORMULA_ID.eq(FORMULAS.ID))
              .join(SENSOR_TYPES)
              .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID))
              .where(SENSOR_PARAMETER.SENSOR_ID.in(sensorIds))
              .fetchGroups(
                  SENSOR_PARAMETER.SENSOR_ID,
                  r ->
                      mapper.toParameterDomain(
                          r.into(SENSOR_PARAMETER), r.into(FORMULAS), r.into(SENSOR_TYPES)));

      data.forEach(s -> s.setParameters(paramsMap.getOrDefault(s.getId(), new ArrayList<>())));
    }

    int totalCount =
        dsl.fetchCount(
            dsl.select(SENSORS.ID)
                .from(SENSORS)
                .leftJoin(EXPERIMENTS)
                .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
                .where(criteria.condition()));

    return new PagedResult<>(data, totalCount);
  }

  @Override
  @Transactional
  public Sensor create(Sensor sensor) {
    SensorsRecord createdSensor = mapper.toRecord(sensor);
    dsl.attach(createdSensor);

    if (sensor.getMainExperiment() != null) {
      createdSensor.setMainExperiment(sensor.getMainExperiment().getId());
    }

    createdSensor.insert();

    Sensor savedSensor = mapper.toDomain(createdSensor);
    savedSensor.setMainExperiment(sensor.getMainExperiment());
    List<SensorParameter> savedParams = new ArrayList<>();

    if (sensor.getParameters() != null) {
      for (SensorParameter p : sensor.getParameters()) {
        FormulasRecord formulaRecord =
            findOrCreateFormulaByExpression(p.getFormula().getExpression());
        SensorTypesRecord typeRecord = findOrCreateSensorTypeByName(p.getType().name());

        SensorParameterRecord paramRecord = mapper.toParameterRecord(p);
        paramRecord.setSensorId(createdSensor.getId());
        paramRecord.setFormulaId(formulaRecord.getId());
        paramRecord.setTypeId(typeRecord.getId());
        paramRecord.insert();

        savedParams.add(mapper.toParameterDomain(paramRecord, formulaRecord, typeRecord));
      }
    }

    savedSensor.setParameters(savedParams);
    return savedSensor;
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
    // fetch existing
    SensorsRecord updatedRecord =
        dsl.selectFrom(SENSORS).where(SENSORS.ID.eq(sensor.getId())).fetchOne();
    if (updatedRecord == null) {
      throw new ObjectBusinessValidationException("object.deleted", Map.of());
    }
    // map new properties to existing
    mapper.updateRecordFromDomain(sensor, updatedRecord);
    updatedRecord.setMainExperiment(
        sensor.getMainExperiment() != null ? sensor.getMainExperiment().getId() : null);

    updatedRecord.update();

    Sensor savedSensor = mapper.toDomain(updatedRecord);
    savedSensor.setMainExperiment(sensor.getMainExperiment());
    List<SensorParameter> savedParams = new ArrayList<>();

    dsl.deleteFrom(SENSOR_PARAMETER).where(SENSOR_PARAMETER.SENSOR_ID.eq(sensor.getId())).execute();

    if (sensor.getParameters() != null) {
      for (SensorParameter p : sensor.getParameters()) {
        FormulasRecord formulaRecord =
            findOrCreateFormulaByExpression(p.getFormula().getExpression());
        SensorTypesRecord typeRecord = findOrCreateSensorTypeByName(p.getType().name());

        SensorParameterRecord paramRecord = mapper.toParameterRecord(p);
        paramRecord.setSensorId(sensor.getId());
        paramRecord.setFormulaId(formulaRecord.getId());
        paramRecord.setTypeId(typeRecord.getId());
        paramRecord.insert();

        savedParams.add(mapper.toParameterDomain(paramRecord, formulaRecord, typeRecord));
      }
    }

    savedSensor.setParameters(savedParams);
    return savedSensor;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Sensor> findById(UUID id) {
    Optional<Sensor> sensorOpt =
        dsl.select(SENSORS.fields())
            .select(EXPERIMENTS.fields())
            .from(SENSORS)
            .leftJoin(EXPERIMENTS)
            .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
            .where(SENSORS.ID.eq(id))
            .fetchOptional(
                r -> {
                  Sensor sensor = mapper.toDomain(r.into(SENSORS));
                  ExperimentsRecord expRecord = r.into(EXPERIMENTS);
                  if (expRecord.getId() != null) {
                    sensor.setMainExperiment(experimentMapper.toDomain(expRecord));
                  }
                  return sensor;
                });

    // Attach parameters
    sensorOpt.ifPresent(
        sensor -> {
          List<SensorParameter> params =
              dsl.select(SENSOR_PARAMETER.fields())
                  .select(FORMULAS.fields())
                  .select(SENSOR_TYPES.fields())
                  .from(SENSOR_PARAMETER)
                  .join(FORMULAS)
                  .on(SENSOR_PARAMETER.FORMULA_ID.eq(FORMULAS.ID))
                  .join(SENSOR_TYPES)
                  .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID))
                  .where(SENSOR_PARAMETER.SENSOR_ID.eq(sensor.getId()))
                  .fetch(
                      r ->
                          mapper.toParameterDomain(
                              r.into(SENSOR_PARAMETER), r.into(FORMULAS), r.into(SENSOR_TYPES)));
          sensor.setParameters(params);
        });

    return sensorOpt;
  }

  private FormulasRecord findOrCreateFormulaByExpression(String expression) {
    // Attempt to insert. If it already exists, do nothing
    dsl.insertInto(FORMULAS)
        .set(FORMULAS.EXPRESSION, expression)
        .onConflict(FORMULAS.EXPRESSION)
        .doNothing()
        .execute();

    // Now we can safely fetch it, knowing it definitively exists
    return dsl.selectFrom(FORMULAS).where(FORMULAS.EXPRESSION.eq(expression)).fetchOne();
  }

  private SensorTypesRecord findOrCreateSensorTypeByName(String name) {
    // Attempt to insert. If it already exists, do nothing
    dsl.insertInto(SENSOR_TYPES)
        .set(SENSOR_TYPES.NAME, name)
        .onConflict(SENSOR_TYPES.NAME)
        .doNothing()
        .execute();

    // Now we can safely fetch it, knowing it definitively exists
    return dsl.selectFrom(SENSOR_TYPES).where(SENSOR_TYPES.NAME.eq(name)).fetchOne();
  }

  @Override
  @Transactional
  public Stream<Sensor> streamUnauditedSensors() {
    List<Sensor> data =
        dsl.select(SENSORS.fields())
            .select(EXPERIMENTS.fields())
            .from(SENSORS)
            .leftJoin(EXPERIMENTS)
            .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID))
            .whereNotExists(
                dsl.selectOne()
                    .from(DSL.table("jv_global_id"))
                    .where(
                        // JaVers stores IDs as their JSON representation, so a UUID id is stored
                        // double-quoted (e.g. "01a2..."). Compare as text instead of casting
                        // local_id to
                        // uuid, which fails on those surrounding quotes.
                        DSL.field("local_id")
                            .eq(
                                DSL.concat(
                                    DSL.inline("\""),
                                    SENSORS.ID.cast(String.class),
                                    DSL.inline("\""))))
                    // Ensure this matches your JaVers @TypeName or class name!
                    .and(DSL.field("type_name").eq(Sensor.JAVERS_TYPE)))
            .fetch(
                r -> {
                  Sensor sensor = mapper.toDomain(r.into(SENSORS));
                  ExperimentsRecord expRecord = r.into(EXPERIMENTS);
                  if (expRecord.getId() != null) {
                    sensor.setMainExperiment(experimentMapper.toDomain(expRecord));
                  }
                  return sensor;
                });

    if (!data.isEmpty()) {
      List<UUID> sensorIds = data.stream().map(Sensor::getId).toList();
      Map<UUID, List<SensorParameter>> paramsMap =
          dsl.select(SENSOR_PARAMETER.fields())
              .select(FORMULAS.fields())
              .select(SENSOR_TYPES.fields())
              .from(SENSOR_PARAMETER)
              .join(FORMULAS)
              .on(SENSOR_PARAMETER.FORMULA_ID.eq(FORMULAS.ID))
              .join(SENSOR_TYPES)
              .on(SENSOR_PARAMETER.TYPE_ID.eq(SENSOR_TYPES.ID))
              .where(SENSOR_PARAMETER.SENSOR_ID.in(sensorIds))
              .fetchGroups(
                  SENSOR_PARAMETER.SENSOR_ID,
                  r ->
                      mapper.toParameterDomain(
                          r.into(SENSOR_PARAMETER), r.into(FORMULAS), r.into(SENSOR_TYPES)));

      data.forEach(s -> s.setParameters(paramsMap.getOrDefault(s.getId(), new ArrayList<>())));
    }

    return data.stream();
  }
}
