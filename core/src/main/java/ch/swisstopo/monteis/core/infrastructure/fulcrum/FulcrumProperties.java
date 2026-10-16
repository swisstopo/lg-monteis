package ch.swisstopo.monteis.core.infrastructure.fulcrum;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// checked once when the context starts, so no caller has to check the token itself. without a
// token Fulcrum is switched off explicitly (enabled=false), not by leaving the token empty
@ConfigurationProperties("monteis.fulcrum")
public record FulcrumProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("https://api.fulcrumapp.com/api") String baseUrl,
    @DefaultValue("") String apiToken,
    @DefaultValue("Mont Terri Monitoring Systems and Sensors") String sensorTable,
    @DefaultValue("Mont Terri Literature") String literatureTable,
    @DefaultValue("20000") int perPage,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("60s") Duration readTimeout) {

  public FulcrumProperties {
    if (enabled && (apiToken == null || apiToken.isBlank())) {
      throw new IllegalStateException(
          "monteis.fulcrum.api-token is required while monteis.fulcrum.enabled is true");
    }
  }

  public void requireEnabled() {
    if (!enabled) {
      throw new FulcrumDisabledException();
    }
  }
}
