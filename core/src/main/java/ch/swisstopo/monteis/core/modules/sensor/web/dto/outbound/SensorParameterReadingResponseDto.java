package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import java.time.Instant;

public record SensorParameterReadingResponseDto(
    String parameter,
    Double value,
    Double rawValue,
    Integer statusCode,
    String status,
    Instant timestamp) {}
