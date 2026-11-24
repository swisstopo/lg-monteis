package ch.swisstopo.monteis.core.modules.notification.web.dto.outbound;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RecipientsResponseDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RecipientCandidateDto> candidates,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "False while the PIs are the Recipients because nobody saved the list yet")
        boolean saved) {}
