package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.SensorType;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;

public record SensorResponseDto(
    Long id,
    String code,
    String name,
    Unit unit,
    SensorType type,
    String comment,
    Double xLocal,
    Double yLocal,
    Double zLocal,
    Double lowerAlarmBound,
    Double upperAlarmBound,
    Boolean active,
    FormulaResponseDto formula,
    Integer version) {}
