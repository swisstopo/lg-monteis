package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteFormulaDto(@NotBlank @Size(max = 1024) String expression) {}
