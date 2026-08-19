package ch.swisstopo.monteis.core.itconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Only active for the {@code e2e-test} profile. The {@code test} profile (used by JUnit ITs)
 * never exercises the SensorService publish path — {@code JooqSensorRepositoryIT} calls the
 * repository directly, and unit tests mock {@code SensorConfigPublisher}/{@code KafkaTemplate} —
 * so it never needs a live broker.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("e2e-test")
public class KafkaTestcontainersConfiguration {

  private static final Logger log = LoggerFactory.getLogger(KafkaTestcontainersConfiguration.class);

  @Bean
  KafkaContainer kafkaContainer() {
    return new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"))
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("KAFKA"));
  }

  @Bean
  DynamicPropertyRegistrar kafkaDynamicPropertyRegistrar(KafkaContainer kafkaContainer) {
    return registry ->
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
  }
}
