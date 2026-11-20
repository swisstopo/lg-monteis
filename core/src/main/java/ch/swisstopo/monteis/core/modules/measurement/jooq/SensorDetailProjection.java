package ch.swisstopo.monteis.core.modules.measurement.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static ch.swisstopo.monteis.core.jooq.generated.tables.SensorReadingSecured.SENSOR_READING_SECURED;

import ch.swisstopo.monteis.contracts.Das;
import ch.swisstopo.monteis.contracts.MeasurementStatus;
import ch.swisstopo.monteis.core.modules.experiment.jooq.ExperimentRecordProjections;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorDetailParameterResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorDetailResponseDto;
import ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound.SensorParameterReadingResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.jooq.SensorRecordProjections;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterResponseDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Record;

/** Builds the sensor detail DTO from the one-row-per-parameter rows of {@code findSensorDetail}. */
final class SensorDetailProjection {

  private SensorDetailProjection() {}

  static Optional<SensorDetailResponseDto> toSensorDetail(List<Record> rows, LocalDate today) {
    List<SensorDetailParameterResponseDto> parameters =
        rows.stream()
            .filter(r -> r.get(SENSOR_PARAMETER.ID) != null)
            .map(SensorDetailProjection::toParameter)
            .toList();

    return rows.stream().findFirst().map(row -> toSensor(row, parameters, today));
  }

  private static SensorDetailResponseDto toSensor(
      Record r, List<SensorDetailParameterResponseDto> parameters, LocalDate today) {
    return new SensorDetailResponseDto(
        r.get(SENSORS.ID),
        r.get(SENSORS.NAME),
        r.get(SENSORS.DAS_SENSOR_ALIAS),
        Das.valueOf(r.get(SENSORS.DAS)),
        r.get(SENSORS.FULCRUM_ID),
        ExperimentRecordProjections.toExperiment(r, today),
        SensorRecordProjections.toCoordinates(r),
        r.get(SENSORS.ACTIVE),
        r.get(SENSORS.COMMENT),
        r.get(SENSORS.VERSION),
        parameters);
  }

  private static SensorDetailParameterResponseDto toParameter(Record r) {
    SensorParameterResponseDto parameter = SensorRecordProjections.toParameter(r);
    return new SensorDetailParameterResponseDto(
        parameter.id(),
        parameter.name(),
        parameter.dasParameterAlias(),
        parameter.type(),
        parameter.unit(),
        parameter.formula(),
        parameter.alarmLimits(),
        parameter.active(),
        parameter.comment(),
        parameter.version(),
        toLatestReading(r));
  }

  private static SensorParameterReadingResponseDto toLatestReading(Record r) {
    var latest = MeasurementQueryRepository.LATEST_READINGS;
    OffsetDateTime timestamp =
        r.get(latest.field(SENSOR_READING_SECURED.TIMESTAMP), OffsetDateTime.class);
    if (timestamp == null) {
      return null;
    }
    return new SensorParameterReadingResponseDto(
        r.get(latest.field(SENSOR_READING_SECURED.NORM_VALUE)),
        r.get(latest.field(SENSOR_READING_SECURED.RAW_VALUE)),
        MeasurementStatus.fromDbValue(
            r.get(latest.field(SENSOR_READING_SECURED.STATUS), String.class)),
        timestamp);
  }
}
