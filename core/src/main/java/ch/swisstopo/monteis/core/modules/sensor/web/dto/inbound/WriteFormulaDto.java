package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import jakarta.validation.constraints.NotBlank;

public record WriteFormulaDto(@NotBlank String expression) {}
