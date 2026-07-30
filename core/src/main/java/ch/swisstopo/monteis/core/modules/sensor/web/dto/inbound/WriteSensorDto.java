package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.sensor.domain.Unit;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.AlarmLimitsDto;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

public record WriteSensorDto(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Long id,
    @NotBlank String code,
    @NotBlank @Size(min = 2, max = 10) String name,
    String comment,
    @NotNull Unit unit,
    @NotNull @Valid WriteSensorTypeDto type,
    @NotNull @Valid CoordinatesDto coordinates,
    @NotNull @Valid AlarmLimitsDto alarmLimits,
    @NotNull Boolean active,
    @Valid WriteFormulaDto formula,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Integer version) {}
