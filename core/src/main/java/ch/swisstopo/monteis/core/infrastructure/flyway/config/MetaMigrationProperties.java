package ch.swisstopo.monteis.core.infrastructure.flyway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.migration.meta")
public record MetaMigrationProperties(
    String dbname, String url, String username, String password, String[] locations) {}
