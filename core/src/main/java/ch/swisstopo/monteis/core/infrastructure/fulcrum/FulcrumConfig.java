package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

// callers get DefaultApi, generated from the Fulcrum spec, as an HTTP service group: endpoint
// paths and parameters are whatever the spec says, an upstream rename becomes a compile error.
// error responses are not translated, GlobalErrorControllerAdvice decodes RestClient's exception
@Configuration
@ImportHttpServices(group = FulcrumConfig.GROUP, types = DefaultApi.class)
public class FulcrumConfig {

  public static final String GROUP = "fulcrum";
  public static final String API_TOKEN_HEADER = "X-ApiToken";

  // the timeouts come from FulcrumProperties like those of FulcrumAttachmentDownloadClient, this
  // runs after Boot's own group configurer and replaces its request factory
  @Bean
  RestClientHttpServiceGroupConfigurer fulcrumGroupConfigurer(FulcrumProperties properties) {
    return groups ->
        groups
            .filterByName(GROUP)
            .forEachClient(
                (group, builder) ->
                    builder
                        .requestFactory(requestFactoryOf(properties))
                        .baseUrl(properties.baseUrl())
                        .defaultHeader(API_TOKEN_HEADER, properties.apiToken())
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE));
  }

  private static ClientHttpRequestFactory requestFactoryOf(FulcrumProperties properties) {
    return ClientHttpRequestFactoryBuilder.detect()
        .build(
            HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout()));
  }
}
