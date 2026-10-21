package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

// against a local http server: the client builds its own request factory from the builder, which
// would replace a MockRestServiceServer
class FulcrumAttachmentDownloadClientTest {

  private static final UUID ATTACHMENT_ID = UUID.fromString("7de093d3-94b1-4fb1-a86a-bf3658003cda");
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
  private static final String TOKEN = "test-token";

  private static final DataSize MAX_SIZE = DataSize.ofMegabytes(200);

  private HttpServer server;
  private final List<String> tokensSentToS3 = new CopyOnWriteArrayList<>();
  private int attachmentStatus = 200;
  private int downloadStatus = 200;
  private String downloadUrl;
  private boolean sendContentLength = true;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/api/v2/attachments/", this::answerAttachment);
    server.createContext("/s3/", this::answerDownload);
    server.createContext("/elsewhere/", this::redirectToDownload);
    server.start();
    downloadUrl = baseUrl() + "/s3/" + ATTACHMENT_ID;
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void should_download_the_content_from_the_url_fulcrum_hands_out() throws IOException {
    // when
    try (InputStream content = client(true).download(ATTACHMENT_ID, MAX_SIZE)) {

      // then
      assertThat(content.readAllBytes()).isEqualTo(CONTENT);
    }
  }

  @Test
  void should_not_send_the_api_token_to_s3() throws IOException {
    // when
    try (InputStream content = client(true).download(ATTACHMENT_ID, MAX_SIZE)) {
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
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("403");
  }

  @Test
  void should_not_download_from_an_origin_fulcrum_should_not_point_to() {
    // given
    downloadUrl = "http://169.254.169.254/latest/meta-data/";

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not to one of");
    assertThat(tokensSentToS3).isEmpty();
  }

  @Test
  void should_not_download_from_the_attachment_host_with_user_info() {
    // given
    downloadUrl = "http://attacker@localhost:" + server.getAddress().getPort() + "/s3/x";

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(IllegalStateException.class);
    assertThat(tokensSentToS3).isEmpty();
  }

  @Test
  void should_not_follow_a_redirect() {
    // given
    downloadUrl = baseUrl() + "/elsewhere/" + ATTACHMENT_ID;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("302");
    assertThat(tokensSentToS3).isEmpty();
  }

  @Test
  void should_refuse_a_file_larger_than_allowed_before_reading_it() {
    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, DataSize.ofBytes(4)))
        .isInstanceOf(FulcrumAttachmentTooLargeException.class);
  }

  @Test
  void should_fail_when_the_download_answers_without_a_content_length() {
    // given
    sendContentLength = false;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without a content length");
  }

  @Test
  void should_fail_when_fulcrum_hands_out_no_url() {
    // given
    downloadUrl = null;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no download url");
  }

  @Test
  void should_translate_a_forbidden_token_into_an_authentication_failure() {
    // given
    attachmentStatus = 403;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(FulcrumAuthenticationException.class)
        .hasMessageContaining("403");
  }

  @Test
  void should_pass_on_any_other_fulcrum_error() {
    // given
    attachmentStatus = 500;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(RestClientResponseException.class);
  }

  @Test
  void should_translate_a_rejected_token_into_an_authentication_failure() {
    // given
    attachmentStatus = 401;

    // when / then
    assertThatThrownBy(() -> client(true).download(ATTACHMENT_ID, MAX_SIZE))
        .isInstanceOf(FulcrumAuthenticationException.class)
        .hasMessageContaining("401");
  }

  @Test
  void should_not_call_fulcrum_while_it_is_switched_off() {
    // when / then
    assertThatThrownBy(() -> client(false).download(ATTACHMENT_ID, MAX_SIZE))
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
            Duration.ofSeconds(5),
            List.of(URI.create(baseUrl())));
    RestClient apiClient = RestClient.builder().apply(FulcrumConfig.configure(properties)).build();
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
          {"id": "%s", "name": "GC-A Experiment.pdf", "url": %s}
          """
              .formatted(ATTACHMENT_ID, downloadUrl == null ? "null" : "\"" + downloadUrl + "\"")
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
      exchange.sendResponseHeaders(downloadStatus, downloadLengthOf(downloadStatus));
      if (downloadStatus == 200) {
        exchange.getResponseBody().write(CONTENT);
      }
    }
  }

  // 0 makes HttpServer send the body chunked, without a content length
  private long downloadLengthOf(int status) {
    if (status != 200) {
      return -1;
    }
    return sendContentLength ? CONTENT.length : 0;
  }

  private void redirectToDownload(HttpExchange exchange) throws IOException {
    try (exchange) {
      exchange.getResponseHeaders().set("Location", baseUrl() + "/s3/" + ATTACHMENT_ID);
      exchange.sendResponseHeaders(302, -1);
    }
  }

  private String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }
}
