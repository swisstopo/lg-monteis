package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FulcrumPropertiesTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void should_refuse_to_start_without_a_token_while_fulcrum_is_enabled(String apiToken) {
    // when / then
    assertThatThrownBy(() -> properties(true, apiToken))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("monteis.fulcrum.api-token");
  }

  @Test
  void should_start_without_a_token_while_fulcrum_is_switched_off() {
    // when / then
    assertThatCode(() -> properties(false, "")).doesNotThrowAnyException();
  }

  @Test
  void should_refuse_a_call_while_fulcrum_is_switched_off() {
    // when / then
    assertThatThrownBy(() -> properties(false, "").requireEnabled())
        .isInstanceOf(FulcrumDisabledException.class);
  }

  @Test
  void should_refuse_to_start_without_an_attachment_origin_while_fulcrum_is_enabled() {
    // when / then
    assertThatThrownBy(() -> properties(true, "token", List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("monteis.fulcrum.attachment-origins");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://attachments.fulcrumapp-eu.com/files",
        "https://attachments.fulcrumapp-eu.com?a=b",
        "attachments.fulcrumapp-eu.com"
      })
  void should_refuse_to_start_with_an_attachment_origin_that_is_more_than_an_origin(
      String attachmentOrigin) {
    // when / then
    assertThatThrownBy(() -> properties(true, "token", List.of(URI.create(attachmentOrigin))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("monteis.fulcrum.attachment-origins");
  }

  @Test
  void should_match_a_url_on_an_attachment_origin_regardless_of_case_and_path() {
    // given
    FulcrumProperties properties =
        properties(true, "token", List.of(URI.create("https://Attachments.fulcrumapp-eu.com/")));

    // when / then
    assertThat(
            properties.isAttachmentOrigin(
                URI.create("HTTPS://attachments.fulcrumapp-eu.com/s3/file.pdf?signature=x")))
        .isTrue();
  }

  @Test
  void should_not_match_a_url_with_user_info_on_an_attachment_origin() {
    // given
    FulcrumProperties properties = properties(true, "token");

    // when / then
    assertThat(
            properties.isAttachmentOrigin(
                URI.create("https://user@attachments.fulcrumapp-eu.com/file.pdf")))
        .isFalse();
  }

  private static FulcrumProperties properties(boolean enabled, String apiToken) {
    return properties(
        enabled, apiToken, List.of(URI.create("https://attachments.fulcrumapp-eu.com")));
  }

  private static FulcrumProperties properties(
      boolean enabled, String apiToken, List<URI> attachmentOrigins) {
    return new FulcrumProperties(
        enabled,
        "https://api.fulcrumapp-eu.com/api",
        apiToken,
        "Mont Terri Monitoring Systems and Sensors",
        "Mont Terri Literature",
        20000,
        Duration.ofSeconds(5),
        Duration.ofSeconds(60),
        attachmentOrigins);
  }
}
