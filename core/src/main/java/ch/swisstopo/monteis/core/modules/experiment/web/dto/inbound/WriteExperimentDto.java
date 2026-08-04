package ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.experiment.domain.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.time.LocalDate;

public record WriteExperimentDto(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Long id,
    @NotNull String name,
    @NotNull String owner,
    String description,
    @NotNull LocalDate experimentStart,
    @NotNull LocalDate experimentEnd,
    Status status) {}
