package ch.swisstopo.monteis.core.itconfig;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.output.Slf4jLogConsumer;

/**
 * Only active for the {@code e2e-test} profile. The {@code test} profile (used by JUnit ITs)
 * binds a fake SecurityContext directly instead of validating real JWTs (see
 * SecurityContextTestSupport), so it never needs a live IdP.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("e2e-test")
public class KeycloakTestcontainersConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(KeycloakTestcontainersConfiguration.class);

  // Fixed to the same host port docker/compose.yml's `keycloak` service uses, so the issuer-uri
  // in application-e2e-test.properties and webapp/public/env.json never need to change per-run.
  private static final int FIXED_HOST_PORT = 18081;
  private static final int CONTAINER_PORT = 8080;

  @Bean
  KeycloakContainer keycloak() {
    String monteisRoot = System.getProperty("monteis.repo.root", "..");
    RealmRenderer.render(monteisRoot);

    FixedPortKeycloakContainer keycloak =
        new FixedPortKeycloakContainer("quay.io/keycloak/keycloak:26.6.4");
    keycloak.withContextPath("/auth");
    keycloak.withRealmImportFile(RealmRenderer.CLASSPATH_RESOURCE_NAME);
    keycloak.withLogConsumer(new Slf4jLogConsumer(log).withPrefix("KEYCLOAK"));
    keycloak.bindFixedPort(FIXED_HOST_PORT, CONTAINER_PORT);
    return keycloak;
  }

  /** Exposes Testcontainers' protected fixed-port API (deliberately discouraged upstream). */
  private static final class FixedPortKeycloakContainer extends KeycloakContainer {
    FixedPortKeycloakContainer(String dockerImageName) {
      super(dockerImageName);
    }

    void bindFixedPort(int hostPort, int containerPort) {
      addFixedExposedPort(hostPort, containerPort);
    }
  }
}
