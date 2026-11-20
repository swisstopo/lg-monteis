package ch.swisstopo.monteis.core.modules.notification.web.dto.outbound;

import ch.swisstopo.monteis.contracts.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record NotifyingParameterResponseDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID sensorParameterId,
    String sensorName,
    String sensorParameterName,
    @Nullable String dasKey,
    String unit,
    @Nullable OffsetDateTime latestReadingTimestamp,
    @Nullable Double latestReadingValue,
    boolean sensorActive,
    boolean sensorParameterActive,
    @Nullable MeasurementStatus measurementStatus) {}
