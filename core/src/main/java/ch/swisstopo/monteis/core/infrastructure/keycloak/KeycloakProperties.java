package ch.swisstopo.monteis.core.infrastructure.keycloak;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("monteis.keycloak")
public record KeycloakProperties(
    String apiUri,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout) {}
