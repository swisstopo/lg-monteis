package ch.swisstopo.monteis.core.infrastructure.s3;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param localEndpoint the S3Mock that dev (docker/compose.yml) and the e2e tests (a testcontainer)
 *     run against. unset in every deployed environment, the client then talks to AWS
 */
@Validated
@ConfigurationProperties("monteis.documents.s3")
public record DocumentStorageProperties(
    @NotBlank String bucket,
    @DefaultValue("eu-central-1") String region,
    @DefaultValue("experiment-documents/") String keyPrefix,
    @Nullable URI localEndpoint) {}
