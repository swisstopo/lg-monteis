package ch.swisstopo.monteis.core.itconfig;

import dasniko.testcontainers.keycloak.KeycloakContainer;

public final class KeycloakTestContainers {

  private static final String IMAGE = "quay.io/keycloak/keycloak:26.6.4";

  private KeycloakTestContainers() {}

  public static KeycloakContainer withLocalRealm() {
    return withLocalRealm(new KeycloakContainer(IMAGE));
  }

  static KeycloakContainer withLocalRealmOnFixedPort(int hostPort, int containerPort) {
    FixedPortKeycloakContainer keycloak = new FixedPortKeycloakContainer(IMAGE);
    keycloak.bindFixedPort(hostPort, containerPort);
    return withLocalRealm(keycloak);
  }

  private static KeycloakContainer withLocalRealm(KeycloakContainer keycloak) {
    RealmRenderer.render(System.getProperty("monteis.repo.root", ".."));
    keycloak.withContextPath("/auth");
    keycloak.withRealmImportFile(RealmRenderer.CLASSPATH_RESOURCE_NAME);
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
