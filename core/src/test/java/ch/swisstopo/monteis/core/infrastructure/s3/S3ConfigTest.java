package ch.swisstopo.monteis.core.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

class S3ConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfiguration.class, S3Config.class)
          .withPropertyValues("monteis.documents.s3.bucket=monteis-documents");

  @Test
  void should_talk_to_aws_without_a_local_endpoint() {
    // when / then
    contextRunner.run(
        context -> assertThat(endpointOverride(context.getBean(S3Client.class))).isNull());
  }

  @Test
  void should_talk_to_the_local_s3_mock_with_a_local_endpoint() {
    // when / then
    contextRunner
        .withPropertyValues("monteis.documents.s3.local-endpoint=http://localhost:9010")
        .run(
            context ->
                assertThat(endpointOverride(context.getBean(S3Client.class)))
                    .isEqualTo(URI.create("http://localhost:9010")));
  }

  @Test
  void should_not_start_without_a_bucket() {
    // when / then
    contextRunner
        .withPropertyValues("monteis.documents.s3.bucket=")
        .run(context -> assertThat(context).hasFailed());
  }

  private static URI endpointOverride(S3Client client) {
    return client.serviceClientConfiguration().endpointOverride().orElse(null);
  }

  @Configuration
  @EnableConfigurationProperties(DocumentStorageProperties.class)
  static class PropertiesConfiguration {}
}
