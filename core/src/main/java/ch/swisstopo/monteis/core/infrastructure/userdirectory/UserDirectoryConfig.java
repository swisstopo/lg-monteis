package ch.swisstopo.monteis.core.infrastructure.userdirectory;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class UserDirectoryConfig {

  @Bean
  UserDirectory userDirectory(
      RestClient.Builder builder,
      UserDirectoryProperties properties,
      ClientRegistrationRepository clientRegistrations,
      OAuth2AuthorizedClientService authorizedClients) {
    return new KeycloakUserDirectory(
        keycloakAdminClient(builder, properties, clientRegistrations, authorizedClients),
        properties.cacheTtl());
  }

  private RestClient keycloakAdminClient(
      RestClient.Builder builder,
      UserDirectoryProperties properties,
      ClientRegistrationRepository clientRegistrations,
      OAuth2AuthorizedClientService authorizedClients) {
    var clientManager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrations, authorizedClients);
    clientManager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());

    // the token belongs to core's service account, not to whoever triggered the request, so one
    // token is shared by all callers instead of one per logged in user
    Authentication serviceAccount =
        UsernamePasswordAuthenticationToken.unauthenticated(
            properties.clientRegistrationId(), null);
    var tokenInterceptor = new OAuth2ClientHttpRequestInterceptor(clientManager);
    tokenInterceptor.setClientRegistrationIdResolver(request -> properties.clientRegistrationId());
    tokenInterceptor.setPrincipalResolver(request -> serviceAccount);

    HttpClientSettings settings =
        HttpClientSettings.defaults()
            .withTimeouts(properties.connectTimeout(), properties.readTimeout());
    return builder
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .baseUrl(properties.adminUri())
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .requestInterceptor(tokenInterceptor)
        .build();
  }
}
