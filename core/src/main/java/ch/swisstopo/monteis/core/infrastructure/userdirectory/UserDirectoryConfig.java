package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.keycloak.KeycloakAdminClient;
import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserDirectoryConfig {

  @Bean
  UserDirectory userDirectory(
      KeycloakAdminClient keycloak,
      CurrentUserProvider currentUser,
      UserDirectoryProperties properties) {
    return new KeycloakUserDirectory(
        keycloak, currentUser, properties.cacheTtl(), properties.cacheMaxSize());
  }
}
