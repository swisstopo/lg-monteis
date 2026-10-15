package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Wiring of the Fulcrum HTTP client.
 *
 * <p>Callers get {@link DefaultApi}, the {@code @HttpExchange} interface generated from the
 * Fulcrum OpenAPI spec, rather than a {@link RestClient}: endpoint paths, query parameter names
 * and their defaults are then whatever the spec says, and an upstream rename becomes a
 * compilation error instead of a request that quietly 404s.
 *
 * <p>Everything the spec cannot express lives here - the host to talk to, the API token and the
 * timeouts. Error responses are deliberately not translated: RestClient raises its own
 * {@code RestClientResponseException} carrying the response body, which
 * {@code GlobalErrorControllerAdvice} decodes into the spec's {@code BadRequestResponse}, so the
 * error contract stays in the one place that owns error handling.
 */
@Configuration
public class FulcrumConfig {

  public static final String API_TOKEN_HEADER = "X-ApiToken";

  @Bean
  DefaultApi fulcrumApi(RestClient.Builder builder, FulcrumProperties properties) {
    return HttpServiceProxyFactory.builderFor(
            RestClientAdapter.create(fulcrumRestClient(builder, properties)))
        .build()
        .createClient(DefaultApi.class);
  }

  @Bean
  FulcrumAttachments fulcrumAttachments(
      DefaultApi fulcrumApi, RestClient.Builder builder, FulcrumProperties properties) {
    RestClient downloadClient = builder.requestFactory(requestFactoryOf(properties)).build();
    return new FulcrumAttachments(fulcrumApi, properties, downloadClient);
  }

  private RestClient fulcrumRestClient(RestClient.Builder builder, FulcrumProperties properties) {
    return builder
        .requestFactory(requestFactoryOf(properties))
        .baseUrl(properties.baseUrl())
        .defaultHeader(API_TOKEN_HEADER, properties.apiToken())
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  private static ClientHttpRequestFactory requestFactoryOf(FulcrumProperties properties) {
    return ClientHttpRequestFactoryBuilder.detect()
        .build(
            HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout()));
  }
}
