package ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeasurementResponseDto(
    UUID sensorId,
    String dasSensorAlias,
    String experimentName,
    String sensorName,
    OffsetDateTime newestMeasurement,
    Double measureValue,
    String unit,
    String sensorType,
    Double x,
    Double y,
    Double z,
    Double alarmLimitFrom,
    Double alarmLimitTo,
    Boolean active,
    String comment) {}
