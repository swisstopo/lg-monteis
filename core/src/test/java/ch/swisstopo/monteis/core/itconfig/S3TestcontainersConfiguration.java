package ch.swisstopo.monteis.core.itconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Only active for the {@code e2e-test} profile, which uploads and downloads documents against the
 * same S3Mock that docker/compose.yml runs for dev. JUnit ITs that need S3 start {@link
 * #s3Mock()} themselves.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("e2e-test")
public class S3TestcontainersConfiguration {

  public static final String BUCKET = "monteis-e2e-documents";
  private static final int PORT = 9090;
  private static final Logger log = LoggerFactory.getLogger(S3TestcontainersConfiguration.class);

  /** An S3Mock that already holds {@link #BUCKET}. */
  public static GenericContainer<?> s3Mock() {
    return new GenericContainer<>("adobe/s3mock:5.2.3")
        .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
        .withExposedPorts(PORT)
        .waitingFor(Wait.forHttp("/favicon.ico").forPort(PORT));
  }

  public static String endpointOf(GenericContainer<?> s3Mock) {
    return "http://" + s3Mock.getHost() + ":" + s3Mock.getMappedPort(PORT);
  }

  @Bean
  GenericContainer<?> s3MockContainer() {
    return s3Mock().withLogConsumer(new Slf4jLogConsumer(log).withPrefix("S3"));
  }

  @Bean
  DynamicPropertyRegistrar s3DynamicPropertyRegistrar(GenericContainer<?> s3MockContainer) {
    return registry -> {
      registry.add("monteis.documents.s3.bucket", () -> BUCKET);
      registry.add("monteis.documents.s3.local-endpoint", () -> endpointOf(s3MockContainer));
    };
  }
}
