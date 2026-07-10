package ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor;

import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.CreateFormulaDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSensorDto(
    @NotNull @NotBlank String code,
    @NotNull @NotBlank String name,
    @NotNull Double lowerBound,
    @NotNull Double upperBound,
    @Valid CreateFormulaDto formula // may be null --> use default mapping in domain!
    ) {}
