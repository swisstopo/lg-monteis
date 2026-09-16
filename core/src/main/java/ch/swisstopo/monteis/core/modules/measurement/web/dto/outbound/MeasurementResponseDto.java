package ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MeasurementResponseDto(
    UUID sensorParameterId,
    String dasKey,
    String experimentName,
    String sensorName,
    String sensorParameterName,
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
    String comment,
    List<ChartPointDto> trend) {}
