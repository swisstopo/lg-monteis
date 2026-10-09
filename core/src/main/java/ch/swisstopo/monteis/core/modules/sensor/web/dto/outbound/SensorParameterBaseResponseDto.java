package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import java.util.UUID;

/**
 * Parameter fields shared by every parameter response. {@link SensorParameterResponseDto} holds
 * them as components, {@link SensorDetailParameterResponseDto} delegates to the parameter it wraps,
 * so neither repeats the field list.
 */
public sealed interface SensorParameterBaseResponseDto
    permits SensorParameterResponseDto, SensorDetailParameterResponseDto {

  UUID id();

  String name();

  String dasParameterAlias();

  SensorTypeResponseDto type();

  Unit unit();

  FormulaResponseDto formula();

  AlarmLimitsDto alarmLimits();

  Boolean active();

  String comment();

  Integer version();
}
