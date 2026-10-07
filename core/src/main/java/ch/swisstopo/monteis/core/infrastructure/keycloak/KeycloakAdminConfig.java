package ch.swisstopo.monteis.core.infrastructure.keycloak;

import ch.swisstopo.monteis.core.infrastructure.security.CurrentUserProvider;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class KeycloakAdminConfig {

  @Bean
  KeycloakAdminClient keycloakAdminClient(
      RestClient.Builder builder,
      KeycloakAdminProperties properties,
      CurrentUserProvider currentUser) {
    HttpClientSettings settings =
        HttpClientSettings.defaults()
            .withTimeouts(properties.connectTimeout(), properties.readTimeout());
    return new KeycloakAdminClient(
        builder
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .baseUrl(properties.adminUri())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .requestInterceptor(callerToken(currentUser))
            .build());
  }

  /**
   * Keycloak decides with the caller's own token what they may see of other users (fine grained
   * admin permissions), so core needs no account of its own on the admin API.
   */
  private static ClientHttpRequestInterceptor callerToken(CurrentUserProvider currentUser) {
    return (request, body, execution) -> {
      String token =
          currentUser
              .currentAccessToken()
              .orElseThrow(
                  () -> new KeycloakUnavailableException("No caller token to ask Keycloak", null));
      request.getHeaders().setBearerAuth(token);
      return execution.execute(request, body);
    };
  }
}
