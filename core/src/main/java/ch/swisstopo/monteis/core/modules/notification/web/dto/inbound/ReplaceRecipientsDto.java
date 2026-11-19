package ch.swisstopo.monteis.core.modules.notification.web.dto.inbound;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReplaceRecipientsDto(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The whole list of Recipients, an empty list means nobody")
        @NotNull
        List<@NotNull UUID> userIds) {}
