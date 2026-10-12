package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FORMULAS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSORS;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_PARAMETER;
import static ch.swisstopo.monteis.core.jooq.generated.Tables.SENSOR_TYPES;

import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorParameterResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
import org.jooq.Record;

/** Read-DTO projections of the sensor/parameter/formula/type join, shared by query repositories. */
public final class SensorRecordProjections {

  private SensorRecordProjections() {}

  public static CoordinatesDto toCoordinates(Record r) {
    return new CoordinatesDto(r.get(SENSORS.X), r.get(SENSORS.Y), r.get(SENSORS.Z));
  }

  public static SensorParameterResponseDto toParameter(Record r) {
    return new SensorParameterResponseDto(
        r.get(SENSOR_PARAMETER.ID),
        r.get(SENSOR_PARAMETER.NAME),
        r.get(SENSOR_PARAMETER.DAS_PARAMETER_ALIAS),
        new SensorTypeResponseDto(
            r.get(SENSOR_TYPES.ID), r.get(SENSOR_TYPES.NAME), r.get(SENSOR_TYPES.VERSION)),
        // jOOQ's generated Unit enum differs from the domain Unit: convert by name.
        Unit.valueOf(r.get(SENSOR_PARAMETER.UNIT).name()),
        new FormulaResponseDto(
            r.get(FORMULAS.ID), r.get(FORMULAS.EXPRESSION), r.get(FORMULAS.VERSION)),
        new AlarmLimitsDto(
            r.get(SENSOR_PARAMETER.LOWER_ALARM_LIMIT), r.get(SENSOR_PARAMETER.UPPER_ALARM_LIMIT)),
        r.get(SENSOR_PARAMETER.ACTIVE),
        r.get(SENSOR_PARAMETER.COMMENT),
        r.get(SENSOR_PARAMETER.VERSION));
  }
}
