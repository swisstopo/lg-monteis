package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import jakarta.validation.constraints.NotBlank;

public record WriteSensorTypeDto(@NotBlank String type) {}
