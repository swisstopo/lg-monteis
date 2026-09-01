package ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.NullOrNotBlank;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.nested.PeriodDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WriteExperimentDto(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        UUID id,
    @NotBlank @Size(min = 2, max = 50) String name,
    @NullOrNotBlank String comment,
    @NotNull @Valid PeriodDto period,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Integer version) {}
