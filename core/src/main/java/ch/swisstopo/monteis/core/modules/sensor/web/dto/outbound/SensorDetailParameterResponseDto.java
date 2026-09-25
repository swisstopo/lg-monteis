package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import java.util.UUID;

public record SensorDetailParameterResponseDto(
    UUID id,
    String name,
    String dasParameterAlias,
    SensorTypeResponseDto type,
    Unit unit,
    FormulaResponseDto formula,
    AlarmLimitsDto alarmLimits,
    Boolean active,
    String comment,
    SensorParameterReadingResponseDto sensorParameterReading,
    Integer version) {}
