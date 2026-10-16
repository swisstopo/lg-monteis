package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// checked once when the context starts, so no caller has to check the token itself. without a
// token Fulcrum is switched off explicitly (enabled=false), not by leaving the token empty.
// attachmentOrigins (scheme, host and port) are the only places a file is downloaded from, the
// download url comes from Fulcrum's answer and is not trusted to point anywhere else
@ConfigurationProperties("monteis.fulcrum")
public record FulcrumProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("https://api.fulcrumapp.com/api") String baseUrl,
    @DefaultValue("") String apiToken,
    @DefaultValue("Mont Terri Monitoring Systems and Sensors") String sensorTable,
    @DefaultValue("Mont Terri Literature") String literatureTable,
    @DefaultValue("20000") int perPage,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("60s") Duration readTimeout,
    @DefaultValue("https://attachments.fulcrumapp-eu.com") List<URI> attachmentOrigins) {

  public FulcrumProperties {
    if (enabled && (apiToken == null || apiToken.isBlank())) {
      throw new IllegalStateException(
          "monteis.fulcrum.api-token is required while monteis.fulcrum.enabled is true");
    }
    if (enabled && attachmentOrigins.isEmpty()) {
      throw new IllegalStateException(
          "monteis.fulcrum.attachment-origins is required while monteis.fulcrum.enabled is true");
    }
    attachmentOrigins = List.copyOf(attachmentOrigins);
  }

  public void requireEnabled() {
    if (!enabled) {
      throw new FulcrumDisabledException();
    }
  }
}
