package ch.swisstopo.monteis.core.infrastructure.flyway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.migration.meta.placeholders")
public record FdwPlaceholderProperties(
    String fdwTsHost,
    Integer fdwTsPort,
    String fdwTsDbname,
    String fdwTsUser,
    String fdwTsPassword,
    String fdwAppUserName) {}
