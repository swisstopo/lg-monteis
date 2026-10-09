package ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record WriteExperimentOwnersDto(@NotNull List<@NotNull UUID> ownerIds) {}
