package ch.swisstopo.monteis.core.modules.notification.web.dto.inbound;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RecipientIdsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<@NotNull UUID> userIds) {}
