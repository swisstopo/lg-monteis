package ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.ExperimentDatesDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

public record WriteExperimentDto(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Long id,
    @NotBlank @Size(min = 2, max = 50) String name,
    String comment,
    @NotNull @Valid ExperimentDatesDto experimentDates,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Integer version) {}
