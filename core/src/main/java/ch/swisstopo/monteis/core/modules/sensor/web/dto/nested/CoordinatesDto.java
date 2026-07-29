package ch.swisstopo.monteis.core.modules.sensor.web.dto.nested;

import jakarta.validation.constraints.NotNull;

public record CoordinatesDto(@NotNull Integer x, @NotNull Integer y, @NotNull Integer z) {}
