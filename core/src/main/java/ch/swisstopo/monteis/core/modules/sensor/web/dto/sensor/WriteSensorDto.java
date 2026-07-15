package ch.swisstopo.monteis.core.modules.sensor.web.dto.sensor;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.formula.WriteFormulaDto;
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
    @NotNull Double lowerBound,
    @NotNull Double upperBound,
    @Valid WriteFormulaDto formula, // may be null --> uses default mapping in domain!
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Integer version) {}
