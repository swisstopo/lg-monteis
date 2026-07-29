package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;

public record SensorResponseDto(
    Long id,
    String code,
    String name,
    Unit unit,
    SensorTypeResponseDto type,
    String comment,
    CoordinatesDto coordinates,
    AlarmLimitsDto alarmLimits,
    Boolean active,
    FormulaResponseDto formula,
    Integer version) {}
