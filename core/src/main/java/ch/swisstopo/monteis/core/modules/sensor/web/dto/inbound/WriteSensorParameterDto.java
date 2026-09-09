package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.NullOrNotBlank;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WriteSensorParameterDto(
    // a missing/unmatched id is inserted as a new row with a freshly generated id;
    // any existing id absent from the incoming list is deleted.
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID id,
    @NotBlank @Size(min = 2, max = 255) String name,
    @NullOrNotBlank @Size(max = 255) String dasParameterAlias,
    @NotNull Unit unit,
    @NotNull @Valid WriteSensorTypeDto type,
    @NotNull @Valid AlarmLimitsDto alarmLimits,
    @NotNull Boolean active,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            description =
                "Omit or send null to default to the identity formula (expression \"x\").")
        @Valid
        WriteFormulaDto formula,
    @NullOrNotBlank @Size(max = 4096) String comment) {}
