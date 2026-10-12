package ch.swisstopo.monteis.core.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
    UUID id, String firstName, String lastName, String email, boolean enabled) {}
