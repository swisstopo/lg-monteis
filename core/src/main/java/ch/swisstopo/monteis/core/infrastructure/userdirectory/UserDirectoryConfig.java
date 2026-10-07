package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakClient;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserDirectoryConfig {

  @Bean
  UserDirectory userDirectory(
      KeycloakClient keycloak,
      CurrentUserProvider currentUser,
      UserDirectoryProperties properties) {
    return new KeycloakUserDirectory(
        keycloak, currentUser, properties.cacheTtl(), properties.cacheMaxSize());
  }
}
