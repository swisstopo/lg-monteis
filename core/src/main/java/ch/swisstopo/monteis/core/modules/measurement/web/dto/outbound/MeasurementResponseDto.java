package ch.swisstopo.monteis.core.modules.measurement.web.dto.outbound;

import ch.swisstopo.monteis.core.modules.measurement.web.dto.nested.ChartPointDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
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
    CoordinatesDto coordinates,
    AlarmLimitsDto alarmLimits,
    Boolean active,
    String comment,
    List<ChartPointDto> trend,
    String measurementState) {}
