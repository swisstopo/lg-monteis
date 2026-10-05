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

  // against AWS the credentials come from the default chain, EKS pod identity in the cluster
  @Bean(destroyMethod = "close")
  S3Client s3Client(DocumentStorageProperties properties) {
    S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
    if (properties.localEndpoint() != null) {
      // the S3Mock checks no signatures, dummy credentials spare an AWS login
      builder
          .endpointOverride(properties.localEndpoint())
          .forcePathStyle(true)
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
    }
    return builder.build();
  }
}
