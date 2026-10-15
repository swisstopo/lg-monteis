package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.swisstopo.monteis.contracts.fulcrum.api.DefaultApi;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class FulcrumAttachmentsTest {

  private static final String BASE_URL = "https://api.fulcrumapp-eu.com/api";
  private static final UUID ATTACHMENT_ID = UUID.fromString("7de093d3-94b1-4fb1-a86a-bf3658003cda");
  private static final String DOWNLOAD_URL =
      "https://fulcrumapp.s3.amazonaws.com/attachments/record/"
          + ATTACHMENT_ID
          + "?X-Amz-Expires=3600";
  private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

  private MockRestServiceServer api;
  private MockRestServiceServer downloads;
  private FulcrumAttachments attachments;

  @BeforeEach
  void setUp() {
    givenAnApiTokenOf("test-token");
  }

  private void givenAnApiTokenOf(String apiToken) {
    FulcrumProperties properties =
        new FulcrumProperties(
            BASE_URL,
            apiToken,
            "Mont Terri Monitoring Systems and Sensors",
            "Mont Terri Literature",
            20000,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60));
    RestClient.Builder apiBuilder =
        RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader(FulcrumConfig.API_TOKEN_HEADER, apiToken);
    api = MockRestServiceServer.bindTo(apiBuilder).build();
    DefaultApi fulcrumApi =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(apiBuilder.build()))
            .build()
            .createClient(DefaultApi.class);
    RestClient.Builder downloadBuilder = RestClient.builder();
    downloads = MockRestServiceServer.bindTo(downloadBuilder).build();
    attachments = new FulcrumAttachments(fulcrumApi, properties, downloadBuilder.build());
  }

  @Test
  void should_download_the_content_from_the_url_fulcrum_hands_out_without_the_token()
      throws IOException {
    // given
    api.expect(requestTo(BASE_URL + "/v2/attachments/" + ATTACHMENT_ID))
        .andExpect(header(FulcrumConfig.API_TOKEN_HEADER, "test-token"))
        .andRespond(withSuccess(attachment(), MediaType.APPLICATION_JSON));
    downloads
        .expect(requestTo(DOWNLOAD_URL))
        .andExpect(headerDoesNotExist(FulcrumConfig.API_TOKEN_HEADER))
        .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_OCTET_STREAM));

    // when
    try (InputStream content = attachments.open(ATTACHMENT_ID)) {

      // then
      assertThat(content.readAllBytes()).isEqualTo(CONTENT);
    }
    api.verify();
    downloads.verify();
  }

  @Test
  void should_fail_when_the_download_is_refused() {
    // given
    api.expect(requestTo(BASE_URL + "/v2/attachments/" + ATTACHMENT_ID))
        .andRespond(withSuccess(attachment(), MediaType.APPLICATION_JSON));
    downloads.expect(requestTo(DOWNLOAD_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

    // when / then
    assertThatThrownBy(() -> attachments.open(ATTACHMENT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("403");
  }

  @Test
  void should_translate_a_rejected_token_into_an_authentication_failure() {
    // given
    api.expect(requestTo(BASE_URL + "/v2/attachments/" + ATTACHMENT_ID))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    // when / then
    assertThatThrownBy(() -> attachments.open(ATTACHMENT_ID))
        .isInstanceOf(FulcrumAuthenticationException.class)
        .hasMessageContaining("401");
  }

  @Test
  void should_fail_without_calling_fulcrum_when_no_api_token_is_configured() {
    // given
    givenAnApiTokenOf("");

    // when / then
    assertThatThrownBy(() -> attachments.open(ATTACHMENT_ID))
        .isInstanceOf(FulcrumAuthenticationException.class);
    api.verify();
    downloads.verify();
  }

  private static String attachment() {
    return """
    {
      "id": "%s",
      "name": "Pesendorfer 02 2023 - GC-A Experiment.pdf",
      "file_size": %d,
      "url": "%s",
      "complete": true,
      "attached_to_type": "record",
      "status": "complete"
    }
    """
        .formatted(ATTACHMENT_ID, CONTENT.length, DOWNLOAD_URL);
  }
}
