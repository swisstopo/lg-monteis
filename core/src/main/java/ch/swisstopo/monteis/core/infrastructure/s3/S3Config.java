package ch.swisstopo.monteis.core.infrastructure.s3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class S3Config {

  // no credentials set: against AWS the default chain finds the EKS pod identity of the cluster
  @Bean(destroyMethod = "close")
  S3Client s3Client(DocumentStorageProperties properties) {
    // the SDK sets no overall timeout, a hanging S3 request only ends on the socket timeout and a
    // slow one never. an attempt has to fit a 50 MB upload, the call covers the retries
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .overrideConfiguration(
                override ->
                    override
                        .apiCallTimeout(properties.callTimeout())
                        .apiCallAttemptTimeout(properties.attemptTimeout()));
    if (properties.localEndpoint() != null) {
      // the S3Mock does not check signatures, but the SDK signs every request and needs credentials
      // for it. dummy ones spare the developer an AWS login
      builder
          .endpointOverride(properties.localEndpoint())
          .forcePathStyle(true)
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
    }
    return builder.build();
  }
}
