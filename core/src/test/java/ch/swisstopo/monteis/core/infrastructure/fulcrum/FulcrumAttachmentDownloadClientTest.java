package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

// against a local http server: the client builds its own request factory from the builder, which
// would replace a MockRestServiceServer
class FulcrumAttachmentDownloadClientTest {

  private static final UUID ATTACHMENT_ID = UUID.fromString("7de093d3-94b1-4fb1-a86a-bf3658003cda");
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final String TOKEN = "test-token";

  private HttpServer server;
  private final List<String> tokensSentToS3 = new CopyOnWriteArrayList<>();
  private int attachmentStatus = 200;
  private int downloadStatus = 200;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/api/v2/attachments/", this::answerAttachment);
    server.createContext("/s3/", this::answerDownload);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void should_download_the_content_from_the_url_fulcrum_hands_out() throws IOException {
    // when
    try (InputStream content = client(true).download(ATTACHMENT_ID)) {

      // then
      assertThat(content.readAllBytes()).isEqualTo(CONTENT);
    }
  }

  @Test
  void should_not_send_the_api_token_to_s3() throws IOException {
    // when
    try (InputStream content = client(true).download(ATTACHMENT_ID)) {
      content.readAllBytes();
    }

    // then
    assertThat(tokensSentToS3).containsOnlyNulls();
  }

  @Test
  void should_fail_when_the_download_is_refused() {
    // given
    downloadStatus = 403;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("403");
  }

  @Test
  void should_translate_a_rejected_token_into_an_authentication_failure() {
    // given
    attachmentStatus = 401;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID))
        .isInstanceOf(FulcrumAuthenticationException.class)
        .hasMessageContaining("401");
  }

  @Test
  void should_not_call_fulcrum_while_it_is_switched_off() {
    // when / then
    assertThatThrownBy(() -> client(false).download(ATTACHMENT_ID))
        .isInstanceOf(FulcrumDisabledException.class);
  }

  private FulcrumAttachmentDownloadClient client(boolean enabled) {
    FulcrumProperties properties =
        new FulcrumProperties(
            enabled,
            baseUrl() + "/api",
            TOKEN,
            "Mont Terri Monitoring Systems and Sensors",
            "Mont Terri Literature",
            20000,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5));
    RestClient apiClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader(FulcrumConfig.API_TOKEN_HEADER, TOKEN)
            .build();
    DefaultApi fulcrumApi =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(apiClient))
            .build()
            .createClient(DefaultApi.class);
    return new FulcrumAttachmentDownloadClient(fulcrumApi, properties, RestClient.builder());
  }

  private void answerAttachment(HttpExchange exchange) throws IOException {
    try (exchange) {
      byte[] body =
          """
          {"id": "%s", "name": "GC-A Experiment.pdf", "file_size": %d, "url": "%s/s3/%s"}
          """
              .formatted(ATTACHMENT_ID, CONTENT.length, baseUrl(), ATTACHMENT_ID)
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(attachmentStatus, attachmentStatus == 200 ? body.length : -1);
      if (attachmentStatus == 200) {
        exchange.getResponseBody().write(body);
      }
    }
  }

  private void answerDownload(HttpExchange exchange) throws IOException {
    try (exchange) {
      tokensSentToS3.add(exchange.getRequestHeaders().getFirst(FulcrumConfig.API_TOKEN_HEADER));
      exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
      exchange.sendResponseHeaders(downloadStatus, downloadStatus == 200 ? CONTENT.length : -1);
      if (downloadStatus == 200) {
        exchange.getResponseBody().write(CONTENT);
      }
    }
  }

  private String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }
}
