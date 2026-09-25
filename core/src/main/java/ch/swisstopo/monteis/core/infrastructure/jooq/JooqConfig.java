package ch.swisstopo.monteis.core.infrastructure.jooq;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.jooq.ConnectionProvider;
import org.jooq.conf.RecordDirtyTracking;
import org.jooq.conf.Settings;
import org.jooq.impl.DataSourceConnectionProvider;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.boot.jooq.autoconfigure.ExceptionTranslatorExecuteListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

@Configuration
public class JooqConfig {

  // SQLSTATE raised when a row-level security WITH CHECK rejects a write (e.g. V15).
  private static final String INSUFFICIENT_PRIVILEGE = "42501";

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

  /**
   * Replaces Spring Boot's default jOOQ exception translator (it backs off via
   * @ConditionalOnMissingBean). Spring maps SQLSTATE 42501 by its class "42" to {@code
   * BadSqlGrammarException}; translate it to {@link PermissionDeniedDataAccessException} instead so
   * a write rejected by RLS surfaces as 403 rather than as a server error.
   */
  @Bean
  public ExceptionTranslatorExecuteListener jooqExceptionTranslator() {
    SQLExceptionTranslator translator =
        new SQLErrorCodeSQLExceptionTranslator("PostgreSQL") {
          @Override
          protected DataAccessException customTranslate(String task, String sql, SQLException ex) {
            if (INSUFFICIENT_PRIVILEGE.equals(ex.getSQLState())) {
              return new PermissionDeniedDataAccessException(buildMessage(task, sql, ex), ex);
            }
            return null;
          }
        };
    return ExceptionTranslatorExecuteListener.of(context -> translator);
  }
}
