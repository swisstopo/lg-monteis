package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record DirectoryUserIdsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<@NotNull UUID> userIds) {}
