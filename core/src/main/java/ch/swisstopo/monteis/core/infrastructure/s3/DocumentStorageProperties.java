package ch.swisstopo.monteis.core.infrastructure.s3;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param bucket blank where the environment has no bucket yet; uploads and downloads then fail,
 *     the rest of the application still starts
 * @param endpoint only set to talk to an S3 compatible store other than AWS (e.g. MinIO locally)
 */
@ConfigurationProperties("monteis.documents.s3")
public record DocumentStorageProperties(
    @DefaultValue("") String bucket,
    @DefaultValue("eu-central-1") String region,
    @DefaultValue("experiment-documents/") String keyPrefix,
    URI endpoint) {}
