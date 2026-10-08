package ch.swisstopo.monteis.core.modules.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The caller's permissions for UI gating only; the backend enforces every rule itself (contract
 * C4).
 */
public record CurrentUserDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "admin role: write access to everything; gates Create Experiment, sensor writes,"
                    + " Sensor menu/route")
        boolean canWriteAll,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "empty when canWriteAll")
        List<UUID> writeExperimentIds) {}
