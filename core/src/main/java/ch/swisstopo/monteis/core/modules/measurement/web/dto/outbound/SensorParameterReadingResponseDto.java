package ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import java.time.OffsetDateTime;

public record SensorParameterReadingResponseDto(
    Double value, Double rawValue, MeasurementStatus status, OffsetDateTime timestamp) {}
