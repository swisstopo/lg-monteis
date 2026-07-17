package ch.swisstopo.monteis.core.infrastructure.jooq;

import javax.sql.DataSource;
import org.jooq.ConnectionProvider;
import org.jooq.conf.RecordDirtyTracking;
import org.jooq.conf.Settings;
import org.jooq.impl.DataSourceConnectionProvider;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

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

  /**
   * Overrides Spring Boot's auto-configured {@code DataSourceConnectionProvider} (it backs off
   * via @ConditionalOnMissingBean) so every jOOQ-acquired connection is tagged with the caller's
   * row-level-security context before use. See {@link RlsConnectionProvider}.
   */
  @Bean
  public ConnectionProvider connectionProvider(DataSource dataSource) {
    return new RlsConnectionProvider(
        new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource)));
  }
}
