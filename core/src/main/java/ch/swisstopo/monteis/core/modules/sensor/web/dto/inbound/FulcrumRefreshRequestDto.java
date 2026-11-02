package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record FulcrumRefreshRequestDto(@NotEmpty List<@NotNull UUID> sensorIds) {}
