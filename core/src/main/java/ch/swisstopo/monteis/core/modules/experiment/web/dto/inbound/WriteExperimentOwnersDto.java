package ch.swisstopo.monteis.core.modules.experiment.web.dto.inbound;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The complete new set of owners, an empty list removes all of them. A list, not a set: the
 * generated TypeScript client turns a set into a JS {@code Set}, which serializes to {@code {}}.
 */
public record WriteExperimentOwnersDto(@NotNull List<@NotNull UUID> ownerIds) {}
