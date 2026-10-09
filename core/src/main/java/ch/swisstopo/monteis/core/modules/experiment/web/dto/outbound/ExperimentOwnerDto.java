package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import java.util.UUID;

public record ExperimentOwnerDto(UUID id, String firstName, String lastName, String email) {}
