package ch.swisstopo.monteis.core.itconfig;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
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
  private static final String ATTACHMENTS_PATH = "/v2/attachments/";
  private static final String FILES_PATH = "/files/";
  private static final String LITERATURE_TABLE = "\"Mont Terri Literature\"";
  private static final String STATEMENT_PARAMETER = "q";

  /** Matches the {@code _record_id = '<uuid>'} filter FulcrumService builds into its SQL. */
  private static final Pattern RECORD_ID_FILTER =
      Pattern.compile("_record_id\\s*=\\s*'([0-9a-fA-F-]{36})'");

  private static final int ANY_FREE_PORT = 0;
  private static final int DEFAULT_BACKLOG = 0;

  @Bean
  FulcrumStub fulcrumStub() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(ANY_FREE_PORT), DEFAULT_BACKLOG);
    FulcrumStub stub = new FulcrumStub(server);
    server.createContext(QUERY_PATH, FulcrumStubConfiguration::handleQuery);
    server.createContext(ATTACHMENTS_PATH, exchange -> handleAttachment(exchange, stub.baseUrl()));
    server.createContext(FILES_PATH, FulcrumStubConfiguration::handleFile);
    server.start();

    log.info("Fulcrum stub listening on {}{}", stub.baseUrl(), QUERY_PATH);
    return stub;
  }

  /**
   * Token the stub is pointed at. The stub serves every request regardless of it, but
   * FulcrumProperties refuses to start without a token while Fulcrum is enabled, so the e2e run
   * would otherwise depend on whatever {@code FULCRUM_API_TOKEN} the machine happens to export -
   * green on a developer box with a real token in docker/.env, red on CI where there is none.
   */
  private static final String STUB_API_TOKEN = "e2e-stub-token";

  @Bean
  DynamicPropertyRegistrar fulcrumStubPropertyRegistrar(FulcrumStub fulcrumStub) {
    return registry -> {
      registry.add("monteis.fulcrum.base-url", fulcrumStub::baseUrl);
      registry.add("monteis.fulcrum.api-token", () -> STUB_API_TOKEN);
      registry.add("monteis.fulcrum.attachment-origins", fulcrumStub::baseUrl);
    };
  }

  private static void handleQuery(HttpExchange exchange) throws IOException {
    try (exchange) {
      String body = queryResponseFor(exchange.getRequestURI());
      log.warn(body);
      sendJson(exchange, body);
    } catch (Exception ex) {
      log.warn("Error:", ex);
    }
  }

  private static String queryResponseFor(URI requestUri) {
    String rawQuery = Objects.requireNonNullElse(requestUri.getRawQuery(), "");
    if (UriUtils.decode(rawQuery, StandardCharsets.UTF_8).contains(LITERATURE_TABLE)) {
      return literatureResponse();
    }
    UUID recordId = recordIdOf(requestUri);
    return recordId == null || UNKNOWN_RECORD_ID.equals(recordId)
        ? emptyResponse()
        : sensorResponse(recordId);
  }

  private static void handleAttachment(HttpExchange exchange, String baseUrl) throws IOException {
    try (exchange) {
      Optional<StubAttachment> attachment =
          StubAttachment.byId(lastPathSegmentOf(exchange.getRequestURI()));
      if (attachment.isEmpty()) {
        exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.value(), -1);
        return;
      }
      sendJson(exchange, attachmentResponse(attachment.get(), baseUrl));
    } catch (Exception ex) {
      log.warn("Error:", ex);
    }
  }

  // fixed length => Content-Length header, which the download client requires
  private static void handleFile(HttpExchange exchange) throws IOException {
    try (exchange) {
      Optional<StubAttachment> attachment =
          StubAttachment.byId(lastPathSegmentOf(exchange.getRequestURI()));
      if (attachment.isEmpty()) {
        exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.value(), -1);
        return;
      }
      byte[] content = attachment.get().content();
      exchange.sendResponseHeaders(HttpStatus.OK.value(), content.length);
      exchange.getResponseBody().write(content);
    } catch (Exception ex) {
      log.warn("Error:", ex);
    }
  }

  private static void sendJson(HttpExchange exchange, String body) throws IOException {
    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    exchange.sendResponseHeaders(HttpStatus.OK.value(), payload.length);
    exchange.getResponseBody().write(payload);
  }

  private static String lastPathSegmentOf(URI requestUri) {
    String path = requestUri.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
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
      // otherwise surface as a puzzling "fulcrum.sensor.not-found" in the browser.
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

  private static String literatureResponse() {
    return """
    {
      "rows": [
        {
          "_record_id": "6f1c2a0e-1d4b-4e8a-9c3f-000000000001",
          "number": "E2E-001",
          "reference_title": "Self-sealing of fractures in Opalinus Clay",
          "author_sequence": "Bossart, P.; Thury, M.",
          "year": 2019,
          "categories": "Technical Report",
          "keywords": ["hydrogeology", "swelling"],
          "document": ["%s"],
          "document_names": ["%s"]
        },
        {
          "_record_id": "6f1c2a0e-1d4b-4e8a-9c3f-000000000002",
          "number": "E2E-002",
          "reference_title": "Gas permeability of the excavation damaged zone",
          "author_sequence": "Marschall, P.",
          "year": 2008,
          "categories": "Data Report",
          "keywords": ["gas"],
          "document": ["%s"],
          "document_names": ["%s"]
        },
        {
          "_record_id": "6f1c2a0e-1d4b-4e8a-9c3f-000000000003",
          "number": "E2E-003",
          "reference_title": "Long-term monitoring of the EZ-B niche",
          "author_sequence": "Nussbaum, C.",
          "year": 2021,
          "categories": "Technical Note",
          "keywords": ["monitoring"],
          "document": ["%s", "%s"],
          "document_names": ["%s", "%s"]
        }
      ]
    }
    """
        .formatted(
            StubAttachment.OPALINUS_REPORT.id,
            StubAttachment.OPALINUS_REPORT.fileName,
            StubAttachment.GAS_MEASUREMENTS.id,
            StubAttachment.GAS_MEASUREMENTS.fileName,
            StubAttachment.NICHE_MONITORING.id,
            StubAttachment.NICHE_MONITORING_APPENDIX.id,
            StubAttachment.NICHE_MONITORING.fileName,
            StubAttachment.NICHE_MONITORING_APPENDIX.fileName);
  }

  private static String attachmentResponse(StubAttachment attachment, String baseUrl) {
    return """
    {"id": "%s", "name": "%s", "url": "%s%s%s"}
    """
        .formatted(attachment.id, attachment.fileName, baseUrl, FILES_PATH, attachment.id);
  }

  /**
   * Files of the literature rows, kept in sync with webapp/e2e/fulcrum-document. The appendix is
   * the second attachment of its row, the sync takes only the first, so it is never downloaded.
   */
  private enum StubAttachment {
    OPALINUS_REPORT("7a2d3b1f-2e5c-4f9b-8d4a-000000000001", "e2e-opalinus-report.pdf"),
    GAS_MEASUREMENTS("7a2d3b1f-2e5c-4f9b-8d4a-000000000002", "e2e-gas-measurements.xlsx"),
    NICHE_MONITORING("7a2d3b1f-2e5c-4f9b-8d4a-000000000003", "e2e-niche-monitoring.pdf"),
    NICHE_MONITORING_APPENDIX(
        "7a2d3b1f-2e5c-4f9b-8d4a-000000000004", "e2e-niche-monitoring-appendix.pdf");

    private final String id;
    private final String fileName;

    StubAttachment(String id, String fileName) {
      this.id = id;
      this.fileName = fileName;
    }

    static Optional<StubAttachment> byId(String id) {
      return Arrays.stream(values()).filter(attachment -> attachment.id.equals(id)).findFirst();
    }

    byte[] content() throws IOException {
      try (InputStream content =
          FulcrumStubConfiguration.class.getResourceAsStream("/fulcrum-stub/" + fileName)) {
        if (content == null) {
          throw new IllegalStateException("No stub file for " + fileName);
        }
        return content.readAllBytes();
      }
    }
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
