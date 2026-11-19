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
                "experiment:write:all: may write every experiment; gates Create Experiment, sensor"
                    + " writes, Sensor menu/route")
        boolean canWriteAllExperiments,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "empty when canWriteAllExperiments")
        List<UUID> writeExperimentIds) {}
