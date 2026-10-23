package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.util.function.Consumer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = FulcrumConfig.GROUP, types = DefaultApi.class)
public class FulcrumConfig {

  public static final String GROUP = "fulcrum";
  public static final String API_TOKEN_HEADER = "X-ApiToken";

  // runs after Boot's group configurer, replaces its request factory to apply our timeouts
  @Bean
  RestClientHttpServiceGroupConfigurer fulcrumGroupConfigurer(FulcrumProperties properties) {
    return groups ->
        groups
            .filterByName(GROUP)
            .forEachClient(
                (group, builder) ->
                    builder
                        .requestFactory(requestFactoryOf(properties))
                        .apply(configure(properties)));
  }

  // refuses every call while Fulcrum off, callers need no check. tests reuse it with own factory
  static Consumer<RestClient.Builder> configure(FulcrumProperties properties) {
    return builder -> {
      builder
          .baseUrl(properties.baseUrl())
          .defaultHeader(API_TOKEN_HEADER, properties.apiToken())
          .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
      if (!properties.enabled()) {
        builder.requestInterceptor(FulcrumConfig::refuseCall);
      }
    };
  }

  private static ClientHttpRequestFactory requestFactoryOf(FulcrumProperties properties) {
    return ClientHttpRequestFactoryBuilder.detect()
        .build(
            HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout()));
  }

  private static ClientHttpResponse refuseCall(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
    throw new FulcrumDisabledException();
  }
}
