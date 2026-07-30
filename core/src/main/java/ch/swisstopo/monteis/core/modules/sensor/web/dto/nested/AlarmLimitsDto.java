package ch.swisstopo.monteis.core.modules.sensor.web.dto.nested;

import jakarta.validation.constraints.NotNull;

public record AlarmLimitsDto(@NotNull Double lower, @NotNull Double upper) {}
