package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import java.util.UUID;

public record SensorTypeResponseDto(UUID id, String name, Integer version) {}
