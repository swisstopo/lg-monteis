package ch.swisstopo.monteis.core.infrastructure.jooq;

import static ch.swisstopo.monteis.core.infrastructure.security.MonteisJwtAuthenticationConverter.READ_ALL_AUTHORITY;

import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jooq.ConnectionProvider;
import org.jooq.exception.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Writes the current {@link Authentication} onto each jOOQ connection as transaction-local
 * Postgres GUCs ({@code app.read_all}, {@code app.user_experiment_ids}), which RLS policies read
 * via {@code current_setting(...)}. Transaction-local, not session-scoped, so values never leak to
 * the next borrower of a pooled connection — callers must run inside a Spring transaction. An
 * unbound or unrecognized {@code Authentication} fails closed (no read-all, no experiment ids)
 * rather than throwing.
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
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    // safe defaults
    boolean readAll = false;
    String experimentIdsCsv = "";

    // find authorities and experiments for current user
    if (authentication != null) {
      readAll =
          authentication.getAuthorities().stream()
              .anyMatch(a -> Objects.equals(a.getAuthority(), READ_ALL_AUTHORITY));
      if (!readAll && authentication.getPrincipal() instanceof MonteisPrincipal principal) {
        experimentIdsCsv =
            principal.getExperimentIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
      }
    }

    // set config for current connection
    setConfig(connection, "app.read_all", String.valueOf(readAll));
    setConfig(connection, "app.user_experiment_ids", experimentIdsCsv);
  }

  // IMPORTANT: use set_config in order to have config for the current transaction only!
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
