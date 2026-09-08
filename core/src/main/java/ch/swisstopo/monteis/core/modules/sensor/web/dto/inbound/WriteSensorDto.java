package ch.swisstopo.monteis.core.modules.sensor.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.Create;
import ch.swisstopo.monteis.core.infrastructure.validation.NullOrNotBlank;
import ch.swisstopo.monteis.core.infrastructure.validation.Update;
import ch.swisstopo.monteis.core.modules.sensor.domain.DAS;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.nested.CoordinatesDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record WriteSensorDto(
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        UUID id,
    @NotBlank @Size(min = 2, max = 50) String name,
    @NullOrNotBlank @Size(max = 255) String dasSensorAlias,
    @NotNull DAS das,
    @NullOrNotBlank @Size(max = 4096) String comment,
    UUID fulcrumId,
    UUID mainExperimentId,
    @NotNull @Valid CoordinatesDto coordinates,
    @NotNull Boolean active,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Integer version,
    @NotNull @Valid @Size(min = 1) List<WriteSensorParameterDto> parameters) {}
