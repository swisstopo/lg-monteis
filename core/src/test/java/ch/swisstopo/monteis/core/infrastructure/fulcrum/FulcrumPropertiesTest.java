package ch.swisstopo.monteis.core.infrastructure.fulcrum;

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
