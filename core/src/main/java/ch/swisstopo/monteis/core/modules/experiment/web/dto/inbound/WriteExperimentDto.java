package ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.ExperimentDatesDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

public record WriteExperimentDto(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Long id,
    @NotNull String name,
    String description,
    @NotNull @Valid ExperimentDatesDto experimentDates,
    Status status,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Integer version) {}
