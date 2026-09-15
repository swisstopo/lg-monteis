package ch.swisstopo.monteis.core.itconfig;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@TestConfiguration(proxyBeanMethods = false)
@Profile("e2e-test")
public class FulcrumStubConfiguration {

  private static final Logger log = LoggerFactory.getLogger(FulcrumStubConfiguration.class);

  /**
   * Record id the stub reports as missing, so the e2e suite can also drive the "Fulcrum has no
   * such record" path. Kept in sync with the constant of the same name in the Playwright tests.
   */
  public static final UUID UNKNOWN_RECORD_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000000");

  public static final double STUB_X = 2579321;

  public static final double STUB_Y = 1247865;
  public static final double STUB_Z = 512;

  private static final String QUERY_PATH = "/v2/query";
  private static final String STATEMENT_PARAMETER = "q";

  /** Matches the {@code _record_id = '<uuid>'} filter FulcrumService builds into its SQL. */
  private static final Pattern RECORD_ID_FILTER =
      Pattern.compile("_record_id\\s*=\\s*'([0-9a-fA-F-]{36})'");

  private static final int ANY_FREE_PORT = 0;
  private static final int DEFAULT_BACKLOG = 0;

  @Bean
  FulcrumStub fulcrumStub() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(ANY_FREE_PORT), DEFAULT_BACKLOG);
    server.createContext(QUERY_PATH, FulcrumStubConfiguration::handleQuery);
    server.start();

    FulcrumStub stub = new FulcrumStub(server);
    log.info("Fulcrum stub listening on {}{}", stub.baseUrl(), QUERY_PATH);
    return stub;
  }

  @Bean
  DynamicPropertyRegistrar fulcrumStubPropertyRegistrar(FulcrumStub fulcrumStub) {
    return registry -> registry.add("monteis.fulcrum.base-url", fulcrumStub::baseUrl);
  }

  private static void handleQuery(HttpExchange exchange) throws IOException {
    try (exchange) {
      UUID recordId = recordIdOf(exchange.getRequestURI());
      String body =
          recordId == null || UNKNOWN_RECORD_ID.equals(recordId)
              ? emptyResponse()
              : sensorResponse(recordId);
      log.warn(body);

      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      exchange.sendResponseHeaders(HttpStatus.OK.value(), payload.length);
      exchange.getResponseBody().write(payload);
    } catch (Exception ex) {
      log.warn("Error:", ex);
    }
  }

  static UUID recordIdOf(URI requestUri) {
    String statement =
        UriComponentsBuilder.fromUri(requestUri)
            .build()
            .getQueryParams()
            .getFirst(STATEMENT_PARAMETER);
    if (statement == null) {
      return null;
    }

    Matcher matcher = RECORD_ID_FILTER.matcher(UriUtils.decode(statement, StandardCharsets.UTF_8));
    if (!matcher.find()) {
      // Only the unfiltered getSensors() statement legitimately lands here. Anything else means
      // the filter FulcrumService writes and the pattern above have drifted apart, which would
      // otherwise surface as a puzzling "object.deleted" in the browser.
      log.warn(
          "No record id filter found in Fulcrum statement, answering with no rows: {}", statement);
      return null;
    }

    return UUID.fromString(matcher.group(1));
  }

  private static String emptyResponse() {
    return "{\"rows\": []}";
  }

  private static String sensorResponse(UUID recordId) {
    return """
    {
      "rows": [
        {
          "_record_id": "%s",
          "_title": "E2E STUB SENSOR",
          "_status": "In progress",
          "_version": 1,
          "system_or_component_category_and_type": ["Sensor", "Point", "T"],
          "automatic_name": "E2E STUB SENSOR",
          "fx_point_with_offset": %s,
          "fy_point_with_offset": %s,
          "fz_point_with_offset": %s
        }
      ]
    }
    """
        .formatted(recordId, STUB_X, STUB_Y, STUB_Z);
  }

  public record FulcrumStub(HttpServer server) implements AutoCloseable {

    private static final int STOP_IMMEDIATELY = 0;

    public String baseUrl() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
      server.stop(STOP_IMMEDIATELY);
    }
  }
}
