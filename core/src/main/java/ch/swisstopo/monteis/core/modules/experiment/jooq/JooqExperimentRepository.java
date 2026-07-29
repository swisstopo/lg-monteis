package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FORMULAS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.ExperimentSensor.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorTypes.SENSOR_TYPES;
import static org.jooq.Records.mapping;
import static org.jooq.impl.DSL.*;

import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentQueryInterface;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.ReadExperimentDetailsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqExperimentRepository implements ExperimentQueryInterface {

  private final DSLContext dsl;

  public JooqExperimentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  @Transactional(readOnly = true) // required for RLS
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
                            SENSORS.UNIT,
                            row(SENSOR_TYPES.ID, SENSOR_TYPES.TYPE, SENSOR_TYPES.VERSION)
                                .mapping(SensorTypeResponseDto::new),
                            SENSORS.COMMENT,
                            row(SENSORS.X, SENSORS.Y, SENSORS.Z).mapping(CoordinatesDto::new),
                            row(SENSORS.LOWER_ALARM_BOUND, SENSORS.UPPER_ALARM_BOUND)
                                .mapping(AlarmLimitsDto::new),
                            SENSORS.ACTIVE,
                            // Map the joined formula into nested FormulaDto
                            row(FORMULAS.ID, FORMULAS.EXPRESSION, FORMULAS.VERSION)
                                .mapping(FormulaResponseDto::new),
                            SENSORS.VERSION)
                        .from(SENSORS)
                        .join(EXPERIMENT_SENSOR)
                        .on(SENSORS.ID.eq(EXPERIMENT_SENSOR.SENSOR_ID))
                        .join(FORMULAS)
                        .on(SENSORS.FORMULA_ID.eq(FORMULAS.ID))
                        .join(SENSOR_TYPES)
                        .on(SENSORS.TYPE_ID.eq(SENSOR_TYPES.ID))
                        .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(EXPERIMENTS.ID)))
                .as("sensors")
                .convertFrom(r -> r.map(mapping(SensorResponseDto::new))))
        .from(EXPERIMENTS)
        .where(EXPERIMENTS.ID.eq(experimentId))
        .fetchOneInto(ReadExperimentDetailsDto.class);
  }
}
