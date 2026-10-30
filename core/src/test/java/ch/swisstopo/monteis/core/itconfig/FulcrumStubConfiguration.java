package ch.swisstopo.monteis.core.itconfig;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@TestConfiguration(proxyBeanMethods = false)
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
    server.createContext(QUERY_PATH, exchange -> handleQuery(exchange, stub));
    server.createContext(ATTACHMENTS_PATH, exchange -> handleAttachment(exchange, stub));
    server.createContext(FILES_PATH, exchange -> handleFile(exchange, stub));
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

  private static void handleQuery(HttpExchange exchange, FulcrumStub stub) throws IOException {
    try (exchange) {
      String body = queryResponseFor(exchange.getRequestURI(), stub);
      log.warn(body);
      sendJson(exchange, body);
    } catch (Exception ex) {
      log.warn("Error:", ex);
    }
  }

  private static String queryResponseFor(URI requestUri, FulcrumStub stub) {
    String rawQuery = Objects.requireNonNullElse(requestUri.getRawQuery(), "");
    if (UriUtils.decode(rawQuery, StandardCharsets.UTF_8).contains(LITERATURE_TABLE)) {
      return literatureResponse(stub.documents());
    }
    UUID recordId = recordIdOf(requestUri);
    return recordId == null || UNKNOWN_RECORD_ID.equals(recordId)
        ? emptyResponse()
        : sensorResponse(recordId);
  }

  private static void handleAttachment(HttpExchange exchange, FulcrumStub stub) throws IOException {
    try (exchange) {
      Optional<StubFile> file = stub.fileOf(lastPathSegmentOf(exchange.getRequestURI()));
      if (file.isEmpty()) {
        exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.value(), -1);
        return;
      }
      sendJson(exchange, attachmentResponse(file.get(), stub.baseUrl()));
    } catch (Exception ex) {
      log.warn("Error:", ex);
    }
  }

  // fixed length => Content-Length header, which the download client requires
  private static void handleFile(HttpExchange exchange, FulcrumStub stub) throws IOException {
    try (exchange) {
      Optional<byte[]> content =
          stub.fileOf(lastPathSegmentOf(exchange.getRequestURI())).flatMap(StubFile::download);
      if (content.isEmpty()) {
        exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.value(), -1);
        return;
      }
      exchange.sendResponseHeaders(HttpStatus.OK.value(), content.get().length);
      exchange.getResponseBody().write(content.get());
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

  private static String literatureResponse(List<StubDocument> documents) {
    return "{\"rows\": [%s]}"
        .formatted(
            String.join(",", documents.stream().map(FulcrumStubConfiguration::rowOf).toList()));
  }

  private static String rowOf(StubDocument document) {
    return """
    {
      "_record_id": "%s",
      "number": %s,
      "reference_title": %s,
      "author_sequence": %s,
      "year": %d,
      "categories": %s,
      "keywords": [%s],
      "document": [%s],
      "document_names": [%s]
    }
    """
        .formatted(
            document.recordId(),
            jsonString(document.number()),
            jsonString(document.title()),
            jsonString(document.authors()),
            document.year(),
            jsonString(document.category()),
            jsonStrings(document.keywords()),
            jsonStrings(document.files().stream().map(f -> f.attachmentId().toString()).toList()),
            jsonStrings(document.files().stream().map(StubFile::name).toList()));
  }

  private static String attachmentResponse(StubFile file, String baseUrl) {
    return """
    {"id": "%s", "name": %s, "url": "%s%s%s"}
    """
        .formatted(
            file.attachmentId(), jsonString(file.name()), baseUrl, FILES_PATH, file.attachmentId());
  }

  private static String jsonStrings(List<String> values) {
    return String.join(",", values.stream().map(FulcrumStubConfiguration::jsonString).toList());
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /** Literature rows of the e2e suite, kept in sync with webapp/e2e/fulcrum-document. */
  static List<StubDocument> e2eDocuments() {
    return List.of(
        new StubDocument(
            UUID.fromString("6f1c2a0e-1d4b-4e8a-9c3f-000000000001"),
            "E2E-001",
            "Self-sealing of fractures in Opalinus Clay",
            "Bossart, P.; Thury, M.",
            2019,
            "Technical Report",
            List.of("hydrogeology", "swelling"),
            List.of(e2eFile("7a2d3b1f-2e5c-4f9b-8d4a-000000000001", "e2e-opalinus-report.pdf"))),
        new StubDocument(
            UUID.fromString("6f1c2a0e-1d4b-4e8a-9c3f-000000000002"),
            "E2E-002",
            "Gas permeability of the excavation damaged zone",
            "Marschall, P.",
            2008,
            "Data Report",
            List.of("gas"),
            List.of(e2eFile("7a2d3b1f-2e5c-4f9b-8d4a-000000000002", "e2e-gas-measurements.xlsx"))),
        new StubDocument(
            UUID.fromString("6f1c2a0e-1d4b-4e8a-9c3f-000000000003"),
            "E2E-003",
            "Long-term monitoring of the EZ-B niche",
            "Nussbaum, C.",
            2021,
            "Technical Note",
            List.of("monitoring"),
            List.of(
                e2eFile("7a2d3b1f-2e5c-4f9b-8d4a-000000000003", "e2e-niche-monitoring.pdf"),
                // sync takes only first attachment, appendix never downloaded
                StubFile.notDownloadable(
                    UUID.fromString("7a2d3b1f-2e5c-4f9b-8d4a-000000000004"),
                    "e2e-niche-monitoring-appendix.pdf"))));
  }

  private static StubFile e2eFile(String attachmentId, String fileName) {
    try (InputStream content =
        FulcrumStubConfiguration.class.getResourceAsStream("/fulcrum-stub/" + fileName)) {
      if (content == null) {
        throw new IllegalStateException("No stub file for " + fileName);
      }
      return new StubFile(UUID.fromString(attachmentId), fileName, content.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public record StubDocument(
      UUID recordId,
      String number,
      String title,
      String authors,
      int year,
      String category,
      List<String> keywords,
      List<StubFile> files) {

    public static StubDocument of(String number, StubFile file) {
      return new StubDocument(
          UUID.randomUUID(),
          number,
          "Title " + number,
          "Author",
          2026,
          "Report",
          List.of(),
          List.of(file));
    }

    public StubDocument withFile(StubFile file) {
      return new StubDocument(
          recordId, number, title, authors, year, category, keywords, List.of(file));
    }
  }

  public record StubFile(UUID attachmentId, String name, byte @Nullable [] content) {

    public static StubFile of(String name, byte[] content) {
      return new StubFile(UUID.randomUUID(), name, content);
    }

    // listed by Fulcrum, file request answers 404
    public static StubFile notDownloadable(UUID attachmentId, String name) {
      return new StubFile(attachmentId, name, null);
    }

    public static StubFile notDownloadable(String name) {
      return notDownloadable(UUID.randomUUID(), name);
    }

    Optional<byte[]> download() {
      return Optional.ofNullable(content);
    }
  }

  public static final class FulcrumStub implements AutoCloseable {

    private static final int STOP_IMMEDIATELY = 0;

    private final HttpServer server;
    private volatile List<StubDocument> documents = e2eDocuments();

    FulcrumStub(HttpServer server) {
      this.server = server;
    }

    public String baseUrl() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    public void serveDocuments(StubDocument... documents) {
      this.documents = List.of(documents);
    }

    public void serveE2eDocuments() {
      this.documents = e2eDocuments();
    }

    List<StubDocument> documents() {
      return documents;
    }

    Optional<StubFile> fileOf(String attachmentId) {
      return documents.stream()
          .flatMap(document -> document.files().stream())
          .filter(file -> file.attachmentId().toString().equals(attachmentId))
          .findFirst();
    }

    @Override
    public void close() {
      server.stop(STOP_IMMEDIATELY);
    }
  }
}
