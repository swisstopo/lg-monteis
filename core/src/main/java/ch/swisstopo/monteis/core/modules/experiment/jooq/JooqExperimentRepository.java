package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentsRecord;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqExperimentRepository implements ExperimentRepository {

  private static final Map<String, Field<?>> COLUMNS_BY_COL_ID =
      Map.of(
          "id", EXPERIMENTS.ID,
          "name", EXPERIMENTS.NAME,
          "period.start", EXPERIMENTS.START,
          "period.end", EXPERIMENTS.END,
          "comment", EXPERIMENTS.COMMENT);

  private final DSLContext dsl;
  private final ExperimentJooqMapper mapper;
  private final CurrentUserProvider currentUserProvider;

  public JooqExperimentRepository(
      DSLContext dsl, ExperimentJooqMapper mapper, CurrentUserProvider currentUserProvider) {
    this.mapper = mapper;
    this.dsl = dsl;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  @Transactional(readOnly = true)
  public Experiment getById(Long experimentId) {

    return dsl.select(
            EXPERIMENTS.ID,
            EXPERIMENTS.NAME,
            EXPERIMENTS.START,
            EXPERIMENTS.END,
            EXPERIMENTS.COMMENT,
            EXPERIMENTS.VERSION,
            DSL.selectCount()
                .from(EXPERIMENT_SENSOR)
                .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(EXPERIMENTS.ID))
                .asField("sensorCount"))
        .from(EXPERIMENTS)
        .where(EXPERIMENTS.ID.eq(experimentId))
        .fetchOne(
            experiment ->
                new Experiment(
                    experiment.get(EXPERIMENTS.ID),
                    experiment.get(EXPERIMENTS.NAME),
                    new Period(experiment.get(EXPERIMENTS.START), experiment.get(EXPERIMENTS.END)),
                    experiment.get(EXPERIMENTS.COMMENT),
                    experiment.get(EXPERIMENTS.VERSION),
                    experiment.get("sensorCount", Integer.class)));
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<Experiment> getExperiments(PagedRequest request) {

    // Default to a deterministic order so offset-based paging stays stable across separate
    // requests (Postgres does not guarantee row order without an ORDER BY).
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(request, COLUMNS_BY_COL_ID, EXPERIMENTS.ID.asc());

    List<Experiment> data =
        dsl.select(
                EXPERIMENTS.ID,
                EXPERIMENTS.NAME,
                EXPERIMENTS.START,
                EXPERIMENTS.END,
                EXPERIMENTS.COMMENT,
                EXPERIMENTS.VERSION,
                DSL.selectCount()
                    .from(EXPERIMENT_SENSOR)
                    .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(EXPERIMENTS.ID))
                    .asField("sensorCount"))
            .from(EXPERIMENTS)
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(request.limit())
            .offset(request.offset())
            .fetch(
                experiment ->
                    new Experiment(
                        experiment.get(EXPERIMENTS.ID),
                        experiment.get(EXPERIMENTS.NAME),
                        new Period(
                            experiment.get(EXPERIMENTS.START), experiment.get(EXPERIMENTS.END)),
                        experiment.get(EXPERIMENTS.COMMENT),
                        experiment.get(EXPERIMENTS.VERSION),
                        experiment.get("sensorCount", Integer.class)));

    int totalCount =
        dsl.fetchCount(dsl.select(EXPERIMENTS.ID).from(EXPERIMENTS).where(criteria.condition()));

    return new PagedResult<>(data, totalCount);
  }

  @Override
  @Transactional
  public Experiment create(Experiment experiment) {
    @SuppressWarnings("java:S2325")
    String currentUser = currentUserProvider.getCurrentUserHandle();

    ExperimentsRecord createdExperiment = mapper.toRecord(experiment);
    dsl.attach(createdExperiment);

    createdExperiment.setOwner(currentUser);

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
    if (updatedRecord == null) {
      throw new ObjectBusinessValidationException("object.deleted", Map.of());
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
    return mapper.toDomain(updatedRecord);
  }

  @Override
  @Transactional
  public Stream<Experiment> streamUnauditedExperiments() {
    return dsl.select(EXPERIMENTS.fields())
        .from(EXPERIMENTS)
        .whereNotExists(
            dsl.selectOne()
                .from(DSL.table("jv_global_id"))
                // JaVers stores IDs as strings, so we cast it to match EXPERIMENTS.ID
                .where(DSL.field("local_id").cast(Long.class).eq(EXPERIMENTS.ID))
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
