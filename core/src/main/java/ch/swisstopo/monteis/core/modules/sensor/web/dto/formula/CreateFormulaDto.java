package ch.swisstopo.monteis.core.modules.sensor.web.dto.formula;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateFormulaDto(Long id, @NotNull @NotBlank String expression, Integer version) {}
