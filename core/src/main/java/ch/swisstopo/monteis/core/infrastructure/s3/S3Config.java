package ch.swisstopo.monteis.core.infrastructure.s3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class S3Config {

  // credentials come from the default chain: EKS pod identity in the cluster, the local AWS
  // profile in development. both are only resolved on the first request
  @Bean(destroyMethod = "close")
  S3Client s3Client(DocumentStorageProperties properties) {
    // explicit, because the MSK IAM auth pulls in a second sync http client and the SDK refuses
    // to pick one on its own
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .httpClientBuilder(ApacheHttpClient.builder());
    if (properties.endpoint() != null) {
      builder.endpointOverride(properties.endpoint()).forcePathStyle(true);
    }
    return builder.build();
  }
}
