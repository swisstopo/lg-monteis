package ch.swisstopo.monteis.core.modules.sensor.web.dto.nested;

import jakarta.validation.constraints.NotNull;

public record RangeLimitsDto(@NotNull Double lower, @NotNull Double upper) {}
