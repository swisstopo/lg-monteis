package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import ch.swisstopo.monteis.core.modules.sensor.web.validation.ValidFormulaState;
import jakarta.validation.constraints.NotBlank;

@ValidFormulaState
public record WriteFormulaDto(Long id, @NotBlank String expression, Integer version) {}
