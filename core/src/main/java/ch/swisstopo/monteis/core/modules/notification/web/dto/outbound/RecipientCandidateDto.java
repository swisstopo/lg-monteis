package ch.swisstopo.monteis.core.modules.notification.web.dto.outbound;

import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUserDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record RecipientCandidateDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DirectoryUserDto user,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "True when the candidate gets the alarms")
        boolean selected) {}
