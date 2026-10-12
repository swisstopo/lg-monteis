package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_ORGANISATION;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

/** Read-DTO projection of the experiments columns, for query repositories joining experiments. */
public final class ExperimentRecordProjections {

  static final String SENSOR_COUNT_FIELD_NAME = "sensorCount";

  static final Field<Integer> SENSOR_COUNT_FIELD =
      DSL.selectCount()
          .from(EXPERIMENT_SENSOR)
          .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(EXPERIMENTS.ID))
          .asField();

  /** Must be selected next to the experiments columns for {@link #toExperiment}. */
  public static final Field<Integer> SENSOR_COUNT = SENSOR_COUNT_FIELD.as(SENSOR_COUNT_FIELD_NAME);

  /** Must be selected next to the experiments columns for {@link #toExperiment}. */
  public static final Field<UUID[]> ORGANISATION_IDS =
      DSL.array(
              DSL.select(EXPERIMENT_ORGANISATION.ORGANISATION_ID)
                  .from(EXPERIMENT_ORGANISATION)
                  .where(EXPERIMENT_ORGANISATION.EXPERIMENT_ID.eq(EXPERIMENTS.ID))
                  .orderBy(EXPERIMENT_ORGANISATION.ORGANISATION_ID))
          .as("organisationIds");

  private ExperimentRecordProjections() {}

  /** Null when the record holds no experiment (e.g. an unmatched LEFT JOIN). */
  public static ExperimentResponseDto toExperiment(Record r, LocalDate today) {
    if (r.get(EXPERIMENTS.ID) == null) {
      return null;
    }
    // Query methods must not use MapStruct mappers, so rebuild via the domain constructor.
    Experiment experiment =
        new Experiment(
            r.get(EXPERIMENTS.ID),
            r.get(EXPERIMENTS.NAME),
            new Period(r.get(EXPERIMENTS.START), r.get(EXPERIMENTS.END)),
            r.get(EXPERIMENTS.COMMENT),
            r.get(EXPERIMENTS.VERSION),
            r.get(SENSOR_COUNT),
            Set.of());
    experiment.setOrganisationIds(List.of(r.get(ORGANISATION_IDS)));
    return new ExperimentResponseDto(
        experiment.getId(),
        experiment.getName(),
        experiment.getComment(),
        new PeriodDto(experiment.getPeriod().start(), experiment.getPeriod().end()),
        experiment.getStatus(today),
        experiment.getVersion(),
        experiment.getSensorCount(),
        // the owners would need a keycloak lookup, the sensor detail does not show them
        List.of(),
        VisibleOwners.NONE.status(),
        experiment.getOrganisationIds());
  }
}
