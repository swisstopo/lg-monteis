package ch.swisstopo.monteis.core.modules.sensor.web.dto.nested;

import jakarta.validation.constraints.NotNull;

public record AlarmLimitsDto(@NotNull Double lowerBound, @NotNull Double upperBound) {}
