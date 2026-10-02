package ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound;

import java.util.UUID;

/** Contact data of an owner, read from Keycloak on every request and never stored. */
public record ExperimentOwnerDto(UUID id, String firstName, String lastName, String email) {}
