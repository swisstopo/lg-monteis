package ch.swisstopo.monteis.core.infrastructure.s3;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

  // credentials come from the default chain, EKS pod identity in the cluster
  @Bean(destroyMethod = "close")
  @Profile("!dev")
  S3Client s3Client(DocumentStorageProperties properties) {
    return S3Client.builder().region(Region.of(properties.region())).build();
  }

  // the S3Mock of docker/compose.yml checks no signatures, so dummy credentials spare the
  // developer an AWS login
  @Bean(destroyMethod = "close")
  @Profile("dev")
  S3Client localS3Client(
      DocumentStorageProperties properties,
      @Value("${monteis.documents.s3.local-endpoint}") URI localEndpoint) {
    return S3Client.builder()
        .region(Region.of(properties.region()))
        .endpointOverride(localEndpoint)
        .forcePathStyle(true)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
        .build();
  }
}
