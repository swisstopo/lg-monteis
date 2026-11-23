package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.NullOrNotBlank;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.RangeLimitsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WriteSensorParameterDto(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Null(groups = Create.class) UUID id,
    @NotBlank @Size(min = 2, max = 255) String name,
    @NullOrNotBlank @Size(max = 255) String dasParameterAlias,
    @NotNull Unit unit,
    @NotNull @Valid WriteSensorTypeDto type,
    @NotNull @Valid AlarmLimitsDto alarmLimits,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Valid RangeLimitsDto rangeLimits,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Omit for false.")
        Boolean notificationActive,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            description = "In seconds, e.g. 9000 for 2.5 hours.")
        @Positive
        Integer timeout,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Positive
        Integer notificationTriggerCount,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Omit for false.")
        Boolean closelyMonitored,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            description = "Required while closelyMonitored is true.")
        @Valid
        AlarmLimitsDto specialAlarmLimits,
    @NotNull Boolean active,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            description =
                "Omit or send null to default to the identity formula (expression \"x\").")
        @Valid
        WriteFormulaDto formula,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Null(groups = Create.class)
        Integer version) {}
