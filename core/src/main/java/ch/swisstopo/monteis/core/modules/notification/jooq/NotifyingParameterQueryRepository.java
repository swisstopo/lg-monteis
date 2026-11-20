package ch.swisstopo.monteis.core.modules.notification.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;
import static org.jooq.Records.mapping;
import static org.jooq.impl.DSL.trueCondition;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.DasKey;
import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.jooq.generated.enums.Unit;
import ch.swisstopo.monteis.core.modules.notification.query.NotifyingParameterQuery;
import ch.swisstopo.monteis.core.modules.notification.web.dto.outbound.NotifyingParameterResponseDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
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
    List<NotifyingParameter> parameters = fetchNotifyingParameters(experimentId);
    Map<UUID, LatestReading> latestReadings = fetchLatestReadingsOf(parameters);
    return parameters.stream()
        .map(parameter -> toDto(parameter, latestReadings.get(parameter.id())))
        .toList();
  }

  private List<NotifyingParameter> fetchNotifyingParameters(UUID experimentId) {
    return dsl.select(
            SENSOR_PARAMETER.ID,
            SENSORS.NAME,
            SENSOR_PARAMETER.NAME,
            SENSORS.DAS,
            SENSORS.DAS_SENSOR_ALIAS,
            SENSOR_PARAMETER.DAS_PARAMETER_ALIAS,
            SENSOR_PARAMETER.UNIT.convertFrom(Unit::name),
            SENSORS.ACTIVE,
            SENSOR_PARAMETER.ACTIVE)
        .from(SENSOR_PARAMETER)
        .join(SENSORS)
        .on(SENSOR_PARAMETER.SENSOR_ID.eq(SENSORS.ID))
        .join(EXPERIMENT_SENSOR)
        .on(EXPERIMENT_SENSOR.SENSOR_ID.eq(SENSORS.ID))
        .where(EXPERIMENT_SENSOR.EXPERIMENT_ID.eq(experimentId))
        .and(notifying())
        .orderBy(SENSORS.NAME.asc(), SENSOR_PARAMETER.NAME.asc())
        .fetch(mapping(NotifyingParameter::new));
  }

  // only seam to MON-235's Notification active flag: true until the flag is readable
  // TODO MON-235: return SENSOR_PARAMETER.NOTIFICATION_ACTIVE.isTrue() (or the shape MON-235 ships)
  private static Condition notifying() {
    return trueCondition();
  }

  // reads the secured view => row-level security applies
  private Map<UUID, LatestReading> fetchLatestReadingsOf(List<NotifyingParameter> parameters) {
    if (parameters.isEmpty()) {
      return Map.of();
    }
    // inlined: as bind values postgres_fdw doesn't push the IN list into the foreign scan
    List<Field<UUID>> parameterIds =
        parameters.stream().map(NotifyingParameter::id).<Field<UUID>>map(DSL::inline).toList();
    return dsl
        .select(
            SENSOR_READING_SECURED.SENSOR_PARAMETER_ID,
            SENSOR_READING_SECURED.TIMESTAMP,
            SENSOR_READING_SECURED.NORM_VALUE,
            SENSOR_READING_SECURED.DAS_KEY,
            SENSOR_READING_SECURED.STATUS.convertFrom(MeasurementStatus::fromDbValue))
        .distinctOn(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID)
        .from(SENSOR_READING_SECURED)
        .where(SENSOR_READING_SECURED.SENSOR_PARAMETER_ID.in(parameterIds))
        .orderBy(
            SENSOR_READING_SECURED.SENSOR_PARAMETER_ID, SENSOR_READING_SECURED.TIMESTAMP.desc())
        .fetch(mapping(LatestReading::new))
        .stream()
        .collect(Collectors.toMap(LatestReading::sensorParameterId, Function.identity()));
  }

  private static NotifyingParameterResponseDto toDto(
      NotifyingParameter parameter, @Nullable LatestReading latest) {
    return new NotifyingParameterResponseDto(
        parameter.id(),
        parameter.sensorName(),
        parameter.name(),
        dasKeyOf(parameter, latest),
        parameter.unit(),
        latest == null ? null : latest.timestamp(),
        latest == null ? null : latest.normValue(),
        parameter.sensorActive(),
        parameter.active(),
        latest == null ? null : latest.status());
  }

  // reading keeps the key valid at ingest, aliases may be remapped since (V14); no alias => no key
  private static @Nullable String dasKeyOf(
      NotifyingParameter parameter, @Nullable LatestReading latest) {
    if (latest != null && latest.dasKey() != null) {
      return latest.dasKey();
    }
    if (parameter.dasSensorAlias() == null || parameter.dasParameterAlias() == null) {
      return null;
    }
    return DasKey.compose(
        Das.valueOf(parameter.das()), parameter.dasSensorAlias(), parameter.dasParameterAlias());
  }

  private record NotifyingParameter(
      UUID id,
      String sensorName,
      String name,
      String das,
      @Nullable String dasSensorAlias,
      @Nullable String dasParameterAlias,
      String unit,
      boolean sensorActive,
      boolean active) {}

  private record LatestReading(
      UUID sensorParameterId,
      OffsetDateTime timestamp,
      @Nullable Double normValue,
      @Nullable String dasKey,
      @Nullable MeasurementStatus status) {}
}
