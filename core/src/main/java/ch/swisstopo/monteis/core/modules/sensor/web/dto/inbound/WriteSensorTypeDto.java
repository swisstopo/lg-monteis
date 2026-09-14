package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteSensorTypeDto(@NotBlank @Size(min = 2, max = 100) String name) {}
