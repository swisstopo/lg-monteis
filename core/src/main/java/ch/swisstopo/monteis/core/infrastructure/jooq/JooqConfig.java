package ch.swisstopo.monteis.core.infrastructure.jooq;

import org.jooq.conf.RecordDirtyTracking;
import org.jooq.conf.Settings;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {
  @Bean
  public DefaultConfigurationCustomizer configurationCustomizer() {
    return c -> {
      Settings settings = c.settings();

      // This flag is required to activate Optimistic Locking globally!
      settings.setExecuteWithOptimisticLocking(true);

      // A field is only flagged as dirty if the value actually changed.
      settings.withRecordDirtyTracking(RecordDirtyTracking.MODIFIED);
    };
  }
}
