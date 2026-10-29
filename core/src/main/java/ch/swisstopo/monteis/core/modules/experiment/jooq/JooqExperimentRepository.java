package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_ORGANISATION;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_OWNER;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.ORGANISATIONS;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectNotFoundException;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.jooq.generated.tables.records.ExperimentsRecord;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentRepository;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectSelectStep;
import org.jooq.exception.DataChangedException;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqExperimentRepository implements ExperimentRepository {

  // Package-private (not private): also reused by JooqExperimentCsvExportQueryRepository.
  static final String SENSOR_COUNT_FIELD_NAME = ExperimentRecordProjections.SENSOR_COUNT_FIELD_NAME;

  static final Field<Integer> SENSOR_COUNT_FIELD = ExperimentRecordProjections.SENSOR_COUNT_FIELD;

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

  // the names sorted and joined, so the experiments sort by their alphabetically first organisation
  // (the filter is ExperimentOrganisationFilter, an experiment has several organisations)
  private static final Field<String> ORGANISATION_NAMES_FIELD =
      DSL.select(DSL.stringAgg(ORGANISATIONS.NAME, ", ").orderBy(ORGANISATIONS.NAME.asc()))
          .from(EXPERIMENT_ORGANISATION)
          .join(ORGANISATIONS)
          .on(ORGANISATIONS.ID.eq(EXPERIMENT_ORGANISATION.ORGANISATION_ID))
          .where(EXPERIMENT_ORGANISATION.EXPERIMENT_ID.eq(EXPERIMENTS.ID))
          .asField();

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
          STATUS_FIELD,
          ExperimentOrganisationFilter.ORGANISATIONS_COLUMN,
          ORGANISATION_NAMES_FIELD);

  private static final List<Field<?>> EXPERIMENT_FIELDS =
      List.of(
          EXPERIMENTS.ID,
          EXPERIMENTS.NAME,
          EXPERIMENTS.START,
          EXPERIMENTS.END,
          EXPERIMENTS.COMMENT,
          EXPERIMENTS.VERSION,
          SENSOR_COUNT_FIELD.as(SENSOR_COUNT_FIELD_NAME),
          OWNER_IDS_FIELD,
          ExperimentRecordProjections.ORGANISATION_IDS);

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
    requireExperimentVisible(experimentId);
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResult<Experiment> getExperiments(PagedRequest request) {

    // Default to a deterministic order so offset-based paging stays stable across separate
    // requests (Postgres does not guarantee row order without an ORDER BY).
    ExperimentOwnerFilter.Split ownerFilter = ExperimentOwnerFilter.split(request);
    ExperimentOrganisationFilter.Split organisationFilter =
        ExperimentOrganisationFilter.split(ownerFilter.requestWithoutOwnersColumn());
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            organisationFilter.requestWithoutOrganisationsColumn(),
            COLUMNS_BY_COL_ID,
            EXPERIMENTS.ID.asc());
    var condition =
        criteria
            .condition()
            .and(ownerFilter.ownerCondition())
            .and(organisationFilter.organisationCondition());

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

    replaceOrganisations(createdExperiment.getId(), experiment.getOrganisationIds());
    Experiment created = mapper.toDomain(createdExperiment);
    loadOrganisationIdsInto(created);
    return created;
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
    boolean organisationsChange = organisationsChange(experiment);

    int updatedRows;
    try {
      updatedRows = updatedRecord.update();
    } catch (DuplicateKeyException _) {
      // unique constraint
      throw new FieldBusinessValidationException(
          "name", experiment.getName(), "validation.unique", Map.of());
    }
    if (updatedRows == 0 && organisationsChange) {
      // a links only change leaves no column dirty, jOOQ then skips the update and with it the
      // version check and the experiments_update policy
      incrementVersion(experiment);
    }
    replaceOrganisations(updatedRecord.getId(), experiment.getOrganisationIds());
    return fetchExperiment(experiment.getId());
  }

  @Override
  @Transactional
  public Experiment replaceOwners(UUID experimentId, Set<UUID> ownerIds) {
    // throws for a hidden experiment before anything is written
    requireExperimentVisible(experimentId);

    dsl.deleteFrom(EXPERIMENT_OWNER)
        .where(EXPERIMENT_OWNER.EXPERIMENT_ID.eq(experimentId))
        .and(EXPERIMENT_OWNER.USER_ID.notIn(ownerIds))
        .execute();
    if (!ownerIds.isEmpty()) {
      dsl.insertInto(EXPERIMENT_OWNER, EXPERIMENT_OWNER.EXPERIMENT_ID, EXPERIMENT_OWNER.USER_ID)
          .valuesOfRows(ownerIds.stream().map(ownerId -> DSL.row(experimentId, ownerId)).toList())
          .onConflictDoNothing()
          .execute();
    }

    return fetchExperiment(experimentId);
  }

  private void requireExperimentVisible(UUID experimentId) {
    if (!dsl.fetchExists(EXPERIMENTS, EXPERIMENTS.ID.eq(experimentId))) {
      throw new ObjectNotFoundException(Experiment.class);
    }
  }

  private Experiment fetchExperiment(UUID experimentId) {
    return selectExperiments()
        .from(EXPERIMENTS)
        .where(EXPERIMENTS.ID.eq(experimentId))
        .fetchOptional(JooqExperimentRepository::toExperiment)
        // RLS hides rows the caller may not read, so hidden and missing look the same
        .orElseThrow(() -> new ObjectNotFoundException(Experiment.class));
  }

  private SelectSelectStep<Record> selectExperiments() {
    return dsl.select(EXPERIMENT_FIELDS);
  }

  private static Experiment toExperiment(Record experiment) {
    Experiment rebuilt =
        new Experiment(
            experiment.get(EXPERIMENTS.ID),
            experiment.get(EXPERIMENTS.NAME),
            new Period(experiment.get(EXPERIMENTS.START), experiment.get(EXPERIMENTS.END)),
            experiment.get(EXPERIMENTS.COMMENT),
            experiment.get(EXPERIMENTS.VERSION),
            experiment.get(SENSOR_COUNT_FIELD_NAME, Integer.class),
            Set.of(experiment.get(OWNER_IDS_FIELD)));
    rebuilt.setOrganisationIds(
        List.of(experiment.get(ExperimentRecordProjections.ORGANISATION_IDS)));
    return rebuilt;
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
              Experiment experiment = mapper.toDomain(r.into(EXPERIMENTS));
              // one query per experiment for its links, fine for a backfill that runs once
              loadOrganisationIdsInto(experiment);
              return experiment;
            });
  }

  private void loadOrganisationIdsInto(Experiment experiment) {
    loadOrganisationIdsInto(List.of(experiment));
  }

  /** Sets the organisation ids of each experiment, with one query for all of them. */
  private void loadOrganisationIdsInto(List<Experiment> experiments) {
    Map<UUID, List<UUID>> organisationIdsByExperiment =
        dsl.select(EXPERIMENT_ORGANISATION.EXPERIMENT_ID, EXPERIMENT_ORGANISATION.ORGANISATION_ID)
            .from(EXPERIMENT_ORGANISATION)
            .where(EXPERIMENT_ORGANISATION.EXPERIMENT_ID.in(idsOf(experiments)))
            // a stable order, javers compares the ids as a list
            .orderBy(EXPERIMENT_ORGANISATION.ORGANISATION_ID)
            .fetchGroups(
                EXPERIMENT_ORGANISATION.EXPERIMENT_ID, EXPERIMENT_ORGANISATION.ORGANISATION_ID);
    experiments.forEach(
        experiment ->
            experiment.setOrganisationIds(
                organisationIdsByExperiment.getOrDefault(experiment.getId(), List.of())));
  }

  /** Increments the version like a column update would, failing for a stale version. */
  private void incrementVersion(Experiment experiment) {
    int updatedRows =
        dsl.update(EXPERIMENTS)
            .set(EXPERIMENTS.VERSION, EXPERIMENTS.VERSION.plus(1))
            .where(EXPERIMENTS.ID.eq(experiment.getId()))
            .and(EXPERIMENTS.VERSION.eq(experiment.getVersion()))
            .execute();
    if (updatedRows == 0) {
      throw new DataChangedException("Database record has been changed");
    }
  }

  private boolean organisationsChange(Experiment experiment) {
    Set<UUID> linked =
        dsl.select(EXPERIMENT_ORGANISATION.ORGANISATION_ID)
            .from(EXPERIMENT_ORGANISATION)
            .where(EXPERIMENT_ORGANISATION.EXPERIMENT_ID.eq(experiment.getId()))
            .fetchSet(EXPERIMENT_ORGANISATION.ORGANISATION_ID);
    return !linked.equals(Set.copyOf(experiment.getOrganisationIds()));
  }

  /** Makes {@code organisationIds} the experiment's organisations: adds the missing links, removes the others. */
  private void replaceOrganisations(UUID experimentId, List<UUID> organisationIds) {
    dsl.deleteFrom(EXPERIMENT_ORGANISATION)
        .where(EXPERIMENT_ORGANISATION.EXPERIMENT_ID.eq(experimentId))
        .and(EXPERIMENT_ORGANISATION.ORGANISATION_ID.notIn(organisationIds))
        .execute();

    if (!organisationIds.isEmpty()) {
      dsl.insertInto(
              EXPERIMENT_ORGANISATION,
              EXPERIMENT_ORGANISATION.EXPERIMENT_ID,
              EXPERIMENT_ORGANISATION.ORGANISATION_ID)
          .valuesOfRows(organisationIds.stream().map(id -> DSL.row(experimentId, id)).toList())
          .onConflictDoNothing()
          .execute();
    }
  }

  private static Collection<UUID> idsOf(List<Experiment> experiments) {
    return experiments.stream().map(Experiment::getId).toList();
  }
}
