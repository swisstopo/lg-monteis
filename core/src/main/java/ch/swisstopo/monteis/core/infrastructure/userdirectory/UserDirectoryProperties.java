package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param cacheMaxSize entries are per caller and experiment, the bound keeps many users browsing
 *     many experiments from growing the cache without limit
 */
@ConfigurationProperties("monteis.user-directory")
public record UserDirectoryProperties(
    @DefaultValue("60s") Duration cacheTtl, @DefaultValue("10000") long cacheMaxSize) {}
