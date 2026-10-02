package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_OWNER;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentsRecord;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectSelectStep;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqExperimentRepository implements ExperimentRepository {

  // Package-private (not private): also reused by JooqExperimentCsvExportQueryRepository.
  static final String SENSOR_COUNT_FIELD_NAME = "sensorCount";

  static final Field<Integer> SENSOR_COUNT_FIELD =
      DSL.selectCount()
          .from(EXPERIMENT_SENSOR)
          .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(EXPERIMENTS.ID))
          .asField();

  static final Field<UUID[]> OWNER_IDS_FIELD =
      DSL.array(
              DSL.select(EXPERIMENT_OWNER.USER_ID)
                  .from(EXPERIMENT_OWNER)
                  .where(EXPERIMENT_OWNER.EXPERIMENT_ID.eq(EXPERIMENTS.ID)))
          .as("ownerIds");

  // Package-private (not private): also reused by JooqExperimentCsvExportQueryRepository, to
  // select the exact same computed "status" value the grid filters/sorts by.
  static final Field<String> STATUS_FIELD =
      DSL.case_()
          .when(
              EXPERIMENTS.START.isNotNull().and(DSL.currentLocalDate().lt(EXPERIMENTS.START)),
              DSL.inline("UPCOMING"))
          .when(
              EXPERIMENTS.END.isNotNull().and(DSL.currentLocalDate().gt(EXPERIMENTS.END)),
              DSL.inline("HISTORIC"))
          .else_(DSL.inline("ACTIVE"));

  // Package-private (not private): also reused by JooqExperimentCsvExportQueryRepository, so a
  // CSV export honors the exact same filter/sort semantics as the grid.
  static final Map<String, Field<?>> COLUMNS_BY_COL_ID =
      Map.of(
          "id",
          EXPERIMENTS.ID,
          "name",
          EXPERIMENTS.NAME,
          "period.start",
          EXPERIMENTS.START,
          "period.end",
          EXPERIMENTS.END,
          "comment",
          EXPERIMENTS.COMMENT,
          SENSOR_COUNT_FIELD_NAME,
          SENSOR_COUNT_FIELD,
          "status",
          STATUS_FIELD);

  private static final List<Field<?>> EXPERIMENT_FIELDS =
      List.of(
          EXPERIMENTS.ID,
          EXPERIMENTS.NAME,
          EXPERIMENTS.START,
          EXPERIMENTS.END,
          EXPERIMENTS.COMMENT,
          EXPERIMENTS.VERSION,
          SENSOR_COUNT_FIELD.as(SENSOR_COUNT_FIELD_NAME),
          OWNER_IDS_FIELD);

  private final DSLContext dsl;
  private final ExperimentJooqMapper mapper;

  public JooqExperimentRepository(DSLContext dsl, ExperimentJooqMapper mapper) {
    this.mapper = mapper;
    this.dsl = dsl;
  }

  @Override
  @Transactional(readOnly = true)
  public Experiment getById(UUID experimentId) {
    return fetchExperiment(experimentId);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireVisible(UUID experimentId) {
    if (!dsl.fetchExists(EXPERIMENTS, EXPERIMENTS.ID.eq(experimentId))) {
      throw new ObjectNotFoundException(Experiment.class);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<Experiment> getExperiments(PagedRequest request) {

    // Default to a deterministic order so offset-based paging stays stable across separate
    // requests (Postgres does not guarantee row order without an ORDER BY).
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            ExperimentOwnerFilter.withoutOwnerFilter(request),
            COLUMNS_BY_COL_ID,
            EXPERIMENTS.ID.asc());
    var condition = criteria.condition().and(ExperimentOwnerFilter.condition(request));

    List<Experiment> data =
        selectExperiments()
            .from(EXPERIMENTS)
            .where(condition)
            .orderBy(criteria.sortFields())
            .limit(request.limit())
            .offset(request.offset())
            .fetch(JooqExperimentRepository::toExperiment);

    int totalCount = dsl.fetchCount(dsl.select(EXPERIMENTS.ID).from(EXPERIMENTS).where(condition));

    return new PagedResult<>(data, totalCount);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Experiment> findAll() {
    return selectExperiments()
        .from(EXPERIMENTS)
        .orderBy(EXPERIMENTS.NAME.asc())
        .fetch(JooqExperimentRepository::toExperiment);
  }

  @Override
  @Transactional
  public Experiment create(Experiment experiment) {
    ExperimentsRecord createdExperiment = mapper.toRecord(experiment);
    dsl.attach(createdExperiment);

    try {
      createdExperiment.insert();
    } catch (DuplicateKeyException _) {
      throw new FieldBusinessValidationException(
          "name", experiment.getName(), "validation.unique", Map.of());
    }

    return mapper.toDomain(createdExperiment);
  }

  @Override
  @Transactional
  public Experiment update(Experiment experiment) {
    // fetch existing
    ExperimentsRecord updatedRecord =
        dsl.selectFrom(EXPERIMENTS).where(EXPERIMENTS.ID.eq(experiment.getId())).fetchOne();
    // a row RLS hides from the caller is indistinguishable from a deleted one (BR4.11)
    if (updatedRecord == null) {
      throw new ObjectNotFoundException(Experiment.class);
    }

    // map new properties to existing
    mapper.updateRecordFromDomain(experiment, updatedRecord);

    try {
      updatedRecord.update();
    } catch (DuplicateKeyException _) {
      // unique constraint
      throw new FieldBusinessValidationException(
          "name", experiment.getName(), "validation.unique", Map.of());
    }
    Experiment updated = mapper.toDomain(updatedRecord);
    // owners are not part of this update, but the audit snapshot must not show them as removed
    updated.setOwnerIds(ownerIdsOf(updated.getId()));
    return updated;
  }

  @Override
  @Transactional
  public Experiment replaceOwners(UUID experimentId, Set<UUID> ownerIds) {
    // throws for a hidden experiment before anything is written
    fetchExperiment(experimentId);

    dsl.deleteFrom(EXPERIMENT_OWNER)
        .where(EXPERIMENT_OWNER.EXPERIMENT_ID.eq(experimentId))
        .and(EXPERIMENT_OWNER.USER_ID.notIn(ownerIds))
        .execute();
    for (UUID ownerId : ownerIds) {
      dsl.insertInto(EXPERIMENT_OWNER, EXPERIMENT_OWNER.EXPERIMENT_ID, EXPERIMENT_OWNER.USER_ID)
          .values(experimentId, ownerId)
          .onConflictDoNothing()
          .execute();
    }

    return fetchExperiment(experimentId);
  }

  private Experiment fetchExperiment(UUID experimentId) {
    return selectExperiments()
        .from(EXPERIMENTS)
        .where(EXPERIMENTS.ID.eq(experimentId))
        .fetchOptional(JooqExperimentRepository::toExperiment)
        // RLS hides rows the caller may not read, so hidden and missing look the same (BR4.11)
        .orElseThrow(() -> new ObjectNotFoundException(Experiment.class));
  }

  private Set<UUID> ownerIdsOf(UUID experimentId) {
    return Set.copyOf(
        dsl.select(EXPERIMENT_OWNER.USER_ID)
            .from(EXPERIMENT_OWNER)
            .where(EXPERIMENT_OWNER.EXPERIMENT_ID.eq(experimentId))
            .fetch(EXPERIMENT_OWNER.USER_ID));
  }

  private SelectSelectStep<Record> selectExperiments() {
    return dsl.select(EXPERIMENT_FIELDS);
  }

  private static Experiment toExperiment(Record experiment) {
    return new Experiment(
        experiment.get(EXPERIMENTS.ID),
        experiment.get(EXPERIMENTS.NAME),
        new Period(experiment.get(EXPERIMENTS.START), experiment.get(EXPERIMENTS.END)),
        experiment.get(EXPERIMENTS.COMMENT),
        experiment.get(EXPERIMENTS.VERSION),
        experiment.get(SENSOR_COUNT_FIELD_NAME, Integer.class),
        Set.of(experiment.get(OWNER_IDS_FIELD)));
  }

  @Override
  @Transactional
  public Stream<Experiment> streamUnauditedExperiments() {
    return dsl.select(EXPERIMENTS.fields())
        .from(EXPERIMENTS)
        .whereNotExists(
            dsl.selectOne()
                .from(DSL.table("jv_global_id"))
                // JaVers stores IDs as their JSON representation, so a UUID id is stored
                // double-quoted (e.g. "01a2..."). Compare as text instead of casting local_id to
                // uuid, which would fail on those surrounding quotes.
                .where(
                    DSL.field("local_id")
                        .eq(
                            DSL.concat(
                                DSL.inline("\""),
                                EXPERIMENTS.ID.cast(String.class),
                                DSL.inline("\""))))
                // Ensure this matches your JaVers @TypeName or class name!
                .and(DSL.field("type_name").eq(Experiment.JAVERS_TYPE)))
        .fetchStream()
        .map(
            r -> {
              ExperimentsRecord experimentsRecord = r.into(EXPERIMENTS);

              return mapper.toDomain(experimentsRecord);
            });
  }
}
