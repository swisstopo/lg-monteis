package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.*;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.*;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.jooq.ExperimentJooqMapper;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import java.util.*;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
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
        sensorsWithExperiments()
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(request.limit())
            .offset(request.offset())
            .fetch(this::toSensorWithExperiment);

    attachParameters(data);

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
    // Make sure the DB's DEFAULT uuidv7() always generates the sensor id
    createdSensor.touched(SENSORS.ID, false);

    if (sensor.getMainExperiment() != null) {
      createdSensor.setMainExperiment(sensor.getMainExperiment().getId());
    }

    try {
      createdSensor.insert();
    } catch (DuplicateKeyException _) {
      // sensors_das_das_sensor_alias_key: a sensor is identified on the DAS supplier's side by
      // (das, das_sensor_alias) together.
      throw dasSensorAliasConflict(sensor);
    }

    Sensor savedSensor = mapper.toDomain(createdSensor);
    savedSensor.setMainExperiment(hydrateMainExperiment(sensor.getMainExperiment()));
    List<SensorParameter> savedParams = new ArrayList<>();

    if (sensor.getParameters() != null) {
      for (SensorParameter p : sensor.getParameters()) {
        FormulasRecord formulaRecord =
            findOrCreateFormulaByExpression(p.getFormula().getExpression());
        SensorTypesRecord typeRecord = findOrCreateSensorTypeByName(p.getType().name());

        SensorParameterRecord paramRecord = mapper.toParameterRecord(p);
        dsl.attach(paramRecord);
        // Make sure the DB's DEFAULT uuidv7() always generates the sensor parameter id
        paramRecord.touched(SENSOR_PARAMETER.ID, false);
        paramRecord.setSensorId(createdSensor.getId());
        paramRecord.setFormulaId(formulaRecord.getId());
        paramRecord.setTypeId(typeRecord.getId());
        try {
          paramRecord.insert();
        } catch (DuplicateKeyException _) {
          // sensor_parameter_sensor_id_das_parameter_alias_key: two parameters of the same
          // sensor can't share a das_parameter_alias (different sensors may reuse one).
          throw dasParameterAliasConflict(p);
        }

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

    try {
      updatedRecord.update();
    } catch (DuplicateKeyException _) {
      throw dasSensorAliasConflict(sensor);
    }

    Sensor savedSensor = mapper.toDomain(updatedRecord);
    savedSensor.setMainExperiment(hydrateMainExperiment(sensor.getMainExperiment()));
    List<SensorParameter> savedParams = new ArrayList<>();

    Set<UUID> idsToRemove =
        new HashSet<>(
            dsl.select(SENSOR_PARAMETER.ID)
                .from(SENSOR_PARAMETER)
                .where(SENSOR_PARAMETER.SENSOR_ID.eq(sensor.getId()))
                .fetchSet(SENSOR_PARAMETER.ID));

    if (sensor.getParameters() != null) {
      for (SensorParameter p : sensor.getParameters()) {
        FormulasRecord formulaRecord =
            findOrCreateFormulaByExpression(p.getFormula().getExpression());
        SensorTypesRecord typeRecord = findOrCreateSensorTypeByName(p.getType().name());

        SensorParameterRecord paramRecord;
        try {
          if (p.getId() != null && idsToRemove.remove(p.getId())) {
            paramRecord =
                dsl.selectFrom(SENSOR_PARAMETER)
                    .where(SENSOR_PARAMETER.ID.eq(p.getId()))
                    .fetchOne();
            mapper.updateParameterRecordFromDomain(p, paramRecord);
            paramRecord.setFormulaId(formulaRecord.getId());
            paramRecord.setTypeId(typeRecord.getId());
            paramRecord.update();
          } else {
            paramRecord = mapper.toParameterRecord(p);
            dsl.attach(paramRecord);
            paramRecord.touched(SENSOR_PARAMETER.ID, false);
            paramRecord.setSensorId(sensor.getId());
            paramRecord.setFormulaId(formulaRecord.getId());
            paramRecord.setTypeId(typeRecord.getId());
            paramRecord.insert();
          }
        } catch (DuplicateKeyException _) {
          // two parameters of the same sensor can't share a das_parameter_alias (different sensors
          // may reuse one).
          throw dasParameterAliasConflict(p);
        }

        savedParams.add(mapper.toParameterDomain(paramRecord, formulaRecord, typeRecord));
      }
    }

    if (!idsToRemove.isEmpty()) {
      dsl.deleteFrom(SENSOR_PARAMETER).where(SENSOR_PARAMETER.ID.in(idsToRemove)).execute();
    }

    savedSensor.setParameters(savedParams);
    return savedSensor;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Sensor> findById(UUID id) {
    Optional<Sensor> sensorOpt =
        sensorsWithExperiments()
            .where(SENSORS.ID.eq(id))
            .fetchOptional(this::toSensorWithExperiment);

    sensorOpt.ifPresent(
        sensor ->
            sensor.setParameters(
                fetchParametersBySensorIds(List.of(sensor.getId()))
                    .getOrDefault(sensor.getId(), new ArrayList<>())));

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
        sensorsWithExperiments()
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
            .fetch(this::toSensorWithExperiment);

    attachParameters(data);

    return data.stream();
  }

  // Default to a plain left join on the experiments table; callers add their own where/order/etc.
  private SelectOnConditionStep<Record> sensorsWithExperiments() {
    return dsl.select(SENSORS.fields())
        .select(EXPERIMENTS.fields())
        .from(SENSORS)
        .leftJoin(EXPERIMENTS)
        .on(SENSORS.MAIN_EXPERIMENT.eq(EXPERIMENTS.ID));
  }

  private Sensor toSensorWithExperiment(Record r) {
    Sensor sensor = mapper.toDomain(r.into(SENSORS));
    ExperimentsRecord expRecord = r.into(EXPERIMENTS);
    if (expRecord.getId() != null) {
      sensor.setMainExperiment(experimentMapper.toDomain(expRecord));
    }
    return sensor;
  }

  // Re-fetch the fully-hydrated Experiment (with its period)
  private Experiment hydrateMainExperiment(Experiment mainExperimentShell) {
    if (mainExperimentShell == null) {
      return null;
    }
    ExperimentsRecord expRecord =
        dsl.selectFrom(EXPERIMENTS)
            .where(EXPERIMENTS.ID.eq(mainExperimentShell.getId()))
            .fetchOne();
    return expRecord != null ? experimentMapper.toDomain(expRecord) : null;
  }

  private void attachParameters(List<Sensor> sensors) {
    if (sensors.isEmpty()) {
      return;
    }
    List<UUID> sensorIds = sensors.stream().map(Sensor::getId).toList();
    Map<UUID, List<SensorParameter>> paramsMap = fetchParametersBySensorIds(sensorIds);
    sensors.forEach(s -> s.setParameters(paramsMap.getOrDefault(s.getId(), new ArrayList<>())));
  }

  private Map<UUID, List<SensorParameter>> fetchParametersBySensorIds(Collection<UUID> sensorIds) {
    return dsl.select(SENSOR_PARAMETER.fields())
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
  }

  private FieldBusinessValidationException dasSensorAliasConflict(Sensor sensor) {
    return dasAliasConflict("dasSensorAlias", sensor.getDasSensorAlias());
  }

  private FieldBusinessValidationException dasParameterAliasConflict(SensorParameter parameter) {
    return dasAliasConflict("dasParameterAlias", parameter.getDasParameterAlias());
  }

  private FieldBusinessValidationException dasAliasConflict(String aliasKey, String alias) {
    return new FieldBusinessValidationException(aliasKey, alias, "validation.unique", Map.of());
  }
}
