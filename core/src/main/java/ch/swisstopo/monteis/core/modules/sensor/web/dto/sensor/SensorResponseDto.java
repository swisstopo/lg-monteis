package ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor;

import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.FormulaResponseDto;

public record SensorResponseDto(
    Long id,
    String code,
    String name,
    Double lowerBound,
    Double upperBound,
    FormulaResponseDto formula,
    Integer version) {}
