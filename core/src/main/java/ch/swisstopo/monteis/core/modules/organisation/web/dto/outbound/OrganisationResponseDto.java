package ch.swisstopo.monteis.core.modules.organisation.web.dto.outbound;

import java.util.UUID;

public record OrganisationResponseDto(UUID id, String name, String comment) {}
