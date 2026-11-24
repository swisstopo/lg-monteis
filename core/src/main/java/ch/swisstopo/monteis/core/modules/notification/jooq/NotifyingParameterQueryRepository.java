package ch.swisstopo.monteis.core.modules.notification.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.LatestReadingProjections.DAS_KEY;
import static ch.swisstopo.monteis.core.modules.measurement.jooq.LatestReadingProjections.LATEST_READINGS;
import static org.jooq.Records.mapping;
import static org.jooq.impl.DSL.trueCondition;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.jooq.generated.enums.Unit;
import ch.swisstopo.monteis.core.modules.notification.query.NotifyingParameterQuery;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotifyingParameterResponseDto;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class NotifyingParameterQueryRepository implements NotifyingParameterQuery {

  private final DSLContext dsl;

  public NotifyingParameterQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<NotifyingParameterResponseDto> findNotifyingParametersOf(UUID experimentId) {
    return dsl.select(
            SENSOR_PARAMETER.ID,
            SENSORS.NAME,
            SENSOR_PARAMETER.NAME,
            DAS_KEY,
            SENSOR_PARAMETER.UNIT.convertFrom(Unit::name),
            LATEST_READINGS.field(SENSOR_READING_SECURED.TIMESTAMP),
            LATEST_READINGS.field(SENSOR_READING_SECURED.NORM_VALUE),
            SENSORS.ACTIVE,
            SENSOR_PARAMETER.ACTIVE,
            LATEST_READINGS
                .field(SENSOR_READING_SECURED.STATUS)
                .convertFrom(MeasurementStatus::fromDbValue))
        .from(SENSOR_PARAMETER)
        .join(SENSORS)
        .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
        .join(EXPERIMENT_SENSOR)
        .on(EXPERIMENT_SENSOR.SENSOR_ID.eq(SENSORS.ID))
        .leftJoin(LATEST_READINGS)
        .on(trueCondition())
        .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(experimentId))
        .and(notifying())
        .orderBy(SENSORS.NAME.asc(), SENSOR_PARAMETER.NAME.asc())
        .fetch(mapping(NotifyingParameterResponseDto::new));
  }

  // only seam to MON-235's Notification active flag: true until the flag is readable
  // TODO MON-235: return SENSOR_PARAMETER.NOTIFICATION_ACTIVE.isTrue() (or the shape MON-235 ships)
  private static Condition notifying() {
    return trueCondition();
  }
}
