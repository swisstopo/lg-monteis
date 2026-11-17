package ch.swisstopo.monteis.core.modules.notification.web.dto.outbound;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record NotifyingParameterResponseDto(
    UUID sensorParameterId,
    String sensorName,
    String sensorParameterName,
    String dasKey,
    String unit,
    @Nullable OffsetDateTime newestMeasurement,
    @Nullable Double measureValue,
    boolean sensorActive,
    boolean sensorParameterActive,
    @Nullable MeasurementStatus measurementStatus) {}
