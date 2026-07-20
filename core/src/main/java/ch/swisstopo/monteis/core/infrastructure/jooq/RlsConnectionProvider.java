package ch.swisstopo.monteis.core.infrastructure.jooq;

import ch.swisstopo.monteis.core.infrastructure.security.SecurityContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.stream.Collectors;
import org.jooq.ConnectionProvider;
import org.jooq.exception.DataAccessException;

/**
 * Propagates the ambient {@link SecurityContext} into the database session on every connection
 * acquisition, via transaction-local Postgres GUCs ({@code set_config(..., true)}), so that
 * row-level security policies and the {@code sensor_reading_secured} view can read them through
 * {@code current_setting(...)}. Transaction-local (not session-scoped) so the values are
 * discarded automatically at COMMIT/ROLLBACK and can never leak to the next borrower of a pooled
 * connection — every RLS-relevant read must therefore run inside a Spring transaction (see
 * {@code @Transactional(readOnly = true)} on the relevant repository methods).
 */
public class RlsConnectionProvider implements ConnectionProvider {

  private final ConnectionProvider delegate;

  public RlsConnectionProvider(ConnectionProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public Connection acquire() {
    Connection connection = delegate.acquire();
    applySecurityContext(connection);
    return connection;
  }

  @Override
  public void release(Connection connection) {
    delegate.release(connection);
  }

  private void applySecurityContext(Connection connection) {
    SecurityContext context = SecurityContext.current();

    setConfig(connection, "app.access_level", context.accessLevel().name());

    String experimentIds =
        context.experimentIds().stream().map(String::valueOf).collect(Collectors.joining(","));
    setConfig(connection, "app.user_experiment_ids", experimentIds);
  }

  private void setConfig(Connection connection, String setting, String value) {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT set_config(?, ?, true)")) {
      statement.setString(1, setting);
      statement.setString(2, value);
      statement.execute();
    } catch (SQLException e) {
      throw new DataAccessException("Failed to set config " + setting, e);
    }
  }
}
