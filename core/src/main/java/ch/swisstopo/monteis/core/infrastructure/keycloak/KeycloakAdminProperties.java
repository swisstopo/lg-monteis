package ch.swisstopo.monteis.core.infrastructure.keycloak;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param adminUri base of the Keycloak admin API for the realm, e.g. {@code
 *     https://host/auth/admin/realms/monteis}
 */
@ConfigurationProperties("monteis.keycloak")
public record KeycloakAdminProperties(
    String adminUri,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout) {}
