package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

@Configuration
public class UserDirectoryConfig {

  @Bean
  UserDirectory userDirectory(RestClient.Builder builder, UserDirectoryProperties properties) {
    return new KeycloakUserDirectory(
        keycloakAdminClient(builder, properties),
        UserDirectoryConfig::currentCaller,
        properties.cacheTtl());
  }

  private RestClient keycloakAdminClient(
      RestClient.Builder builder, UserDirectoryProperties properties) {
    HttpClientSettings settings =
        HttpClientSettings.defaults()
            .withTimeouts(properties.connectTimeout(), properties.readTimeout());
    return builder
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .baseUrl(properties.adminUri())
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .requestInterceptor(callerToken())
        .build();
  }

  /**
   * Keycloak decides with the caller's own token what they may see of other users (fine grained
   * admin permissions), so core needs no account of its own on the admin API.
   */
  private static ClientHttpRequestInterceptor callerToken() {
    return (request, body, execution) -> {
      Jwt jwt =
          currentJwt()
              .orElseThrow(
                  () ->
                      new UserDirectoryUnavailableException(
                          "No caller token to ask Keycloak with", null));
      request.getHeaders().setBearerAuth(jwt.getTokenValue());
      return execution.execute(request, body);
    };
  }

  private static Optional<Jwt> currentJwt() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getCredentials() instanceof Jwt jwt) {
      return Optional.of(jwt);
    }
    return Optional.empty();
  }

  private static Optional<UUID> currentCaller() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof MonteisPrincipal principal) {
      return Optional.of(principal.getSubject());
    }
    return Optional.empty();
  }
}
