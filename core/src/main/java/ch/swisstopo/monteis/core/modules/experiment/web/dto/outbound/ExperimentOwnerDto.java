package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ExperimentOwnerDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    String firstName,
    String lastName,
    String email) {}
