package ch.swisstopo.monteis.core.infrastructure.s3;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("monteis.documents.s3")
public record DocumentStorageProperties(
    @NotBlank String bucket,
    @DefaultValue("eu-central-1") String region,
    @DefaultValue("experiment-documents/") String keyPrefix) {}
