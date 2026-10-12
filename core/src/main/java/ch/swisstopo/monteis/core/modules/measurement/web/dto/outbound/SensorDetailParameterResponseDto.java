package ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.SensorTypeResponseDto;
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
    Integer version,
    SensorParameterReadingResponseDto latestParameterReadingValue) {}
