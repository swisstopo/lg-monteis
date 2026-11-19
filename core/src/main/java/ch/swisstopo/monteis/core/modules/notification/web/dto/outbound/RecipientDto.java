package ch.swisstopo.monteis.core.modules.notification.web.dto.outbound;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record RecipientDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    String firstName,
    String lastName,
    String email) {}
