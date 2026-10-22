package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// empty token rejected, switch off via enabled=false. download url comes from Fulcrum's answer,
// only attachmentOrigins trusted
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
    @DefaultValue("https://content.fulcrumapp-eu.com") List<URI> attachmentOrigins) {

  public FulcrumProperties {
    if (enabled && (apiToken == null || apiToken.isBlank())) {
      throw new IllegalStateException(
          "monteis.fulcrum.api-token is required while monteis.fulcrum.enabled is true");
    }
    if (enabled && attachmentOrigins.isEmpty()) {
      throw new IllegalStateException(
          "monteis.fulcrum.attachment-origins is required while monteis.fulcrum.enabled is true");
    }
    attachmentOrigins =
        attachmentOrigins.stream().map(FulcrumProperties::requireOnlyOrigin).toList();
  }

  public boolean isAttachmentOrigin(URI url) {
    return attachmentOrigins.contains(originOf(url));
  }

  // authority keeps user info, so user@host != host
  static URI originOf(URI url) {
    return URI.create((url.getScheme() + "://" + url.getRawAuthority()).toLowerCase(Locale.ROOT));
  }

  private static URI requireOnlyOrigin(URI configured) {
    String path = Objects.requireNonNullElse(configured.getRawPath(), "");
    if (configured.getScheme() == null
        || configured.getHost() == null
        || !(path.isEmpty() || path.equals("/"))
        || configured.getRawQuery() != null
        || configured.getRawFragment() != null) {
      throw new IllegalStateException(
          "monteis.fulcrum.attachment-origins takes scheme, host and port only, not %s"
              .formatted(configured));
    }
    return originOf(configured);
  }
}
