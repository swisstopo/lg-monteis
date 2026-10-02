package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param adminUri base of the Keycloak admin API for the realm, e.g. {@code
 *     https://host/auth/admin/realms/monteis}
 * @param clientRegistrationId the {@code spring.security.oauth2.client.registration} entry whose
 *     service account reads the users
 */
@ConfigurationProperties("monteis.user-directory")
public record UserDirectoryProperties(
    String adminUri,
    @DefaultValue("monteis-core") String clientRegistrationId,
    @DefaultValue("60s") Duration cacheTtl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout) {}
