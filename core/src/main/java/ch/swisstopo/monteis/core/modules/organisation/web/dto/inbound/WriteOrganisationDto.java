package ch.swisstopo.monteis.core.modules.organisation.web.dto.inbound;

import ch.swisstopo.monteis.core.infrastructure.validation.NullOrNotBlank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteOrganisationDto(
    @NotBlank @Size(min = 2, max = 100) String name,
    @NullOrNotBlank @Size(max = 4096) String comment) {}
