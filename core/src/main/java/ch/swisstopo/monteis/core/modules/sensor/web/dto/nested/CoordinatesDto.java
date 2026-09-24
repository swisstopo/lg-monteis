package ch.swisstopo.monteis.core.modules.sensor.web.dto.nested;

import jakarta.validation.constraints.NotNull;

public record CoordinatesDto(@NotNull Double x, @NotNull Double y, @NotNull Double z) {}
