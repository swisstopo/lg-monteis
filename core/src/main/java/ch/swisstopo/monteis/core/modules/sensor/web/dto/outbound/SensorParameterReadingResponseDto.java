package ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.sensor.domain.MeasurementStatus;
import java.time.OffsetDateTime;

public record SensorParameterReadingResponseDto(
    Double value, Double rawValue, MeasurementStatus status, OffsetDateTime timestamp) {}
