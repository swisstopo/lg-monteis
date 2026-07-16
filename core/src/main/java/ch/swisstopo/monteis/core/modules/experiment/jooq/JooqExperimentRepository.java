package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FORMULAS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.ExperimentSensor.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static org.jooq.Records.mapping;
import static org.jooq.impl.DSL.*;

import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQueryInterface;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.ReadExperimentDetailsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqExperimentRepository implements ExperimentQueryInterface {

  private final DSLContext dsl;

  public JooqExperimentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public ReadExperimentDetailsDto getExperimentDetails(Long experimentId) {
    return dsl.select(
            EXPERIMENTS.ID,
            EXPERIMENTS.NAME,
            EXPERIMENTS.DESCRIPTION,
            EXPERIMENTS.VERSION,

            // The Multiset automatically creates the List<SensorResponseDto>
            multiset(
                    select(
                            SENSORS.ID,
                            SENSORS.CODE,
                            SENSORS.NAME,
                            SENSORS.LOWER_BOUND,
                            SENSORS.UPPER_BOUND,
                            // Map the joined formula into your nested FormulaDto!
                            row(FORMULAS.ID, FORMULAS.EXPRESSION, FORMULAS.VERSION)
                                .mapping(FormulaResponseDto::new),
                            SENSORS.VERSION // <-- MOVED TO END
                            )
                        .from(SENSORS)
                        .join(EXPERIMENT_SENSOR)
                        .on(SENSORS.ID.eq(EXPERIMENT_SENSOR.SENSOR_ID))
                        .join(FORMULAS)
                        .on(SENSORS.FORMULA_ID.eq(FORMULAS.ID)) // Join Formula here
                        .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(EXPERIMENTS.ID)))
                .as("sensors")
                .convertFrom(r -> r.map(mapping(SensorResponseDto::new))))
        .from(EXPERIMENTS)
        .where(EXPERIMENTS.ID.eq(experimentId))
        .fetchOneInto(ReadExperimentDetailsDto.class);
  }
}
