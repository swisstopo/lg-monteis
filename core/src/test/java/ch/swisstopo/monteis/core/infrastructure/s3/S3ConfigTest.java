package ch.swisstopo.monteis.core.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

class S3ConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(PropertiesConfiguration.class, S3Config.class)
          .withPropertyValues("monteis.documents.s3.bucket=monteis-documents");

  @Test
  void should_talk_to_aws_outside_dev_and_e2e() {
    contextRunner
        .withPropertyValues("spring.profiles.active=prod")
        .run(context -> assertThat(endpointOverride(context.getBean(S3Client.class))).isNull());
  }

  @ParameterizedTest
  @ValueSource(strings = {"dev", "e2e-test"})
  void should_talk_to_the_local_s3_mock_in(String profile) {
    contextRunner
        .withPropertyValues(
            "spring.profiles.active=" + profile,
            "monteis.documents.s3.local-endpoint=http://localhost:9010")
        .run(
            context ->
                assertThat(endpointOverride(context.getBean(S3Client.class)))
                    .isEqualTo(URI.create("http://localhost:9010")));
  }

  @Test
  void should_not_start_without_a_bucket() {
    contextRunner
        .withPropertyValues("spring.profiles.active=prod", "monteis.documents.s3.bucket=")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void should_default_region_and_key_prefix() {
    contextRunner
        .withPropertyValues("spring.profiles.active=prod")
        .run(
            context -> {
              DocumentStorageProperties properties =
                  context.getBean(DocumentStorageProperties.class);
              assertThat(properties.region()).isEqualTo("eu-central-1");
              assertThat(properties.keyPrefix()).isEqualTo("experiment-documents/");
            });
  }

  private static URI endpointOverride(S3Client client) {
    return client.serviceClientConfiguration().endpointOverride().orElse(null);
  }

  @Configuration
  @EnableConfigurationProperties(DocumentStorageProperties.class)
  static class PropertiesConfiguration {}
}
