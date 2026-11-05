package ch.swisstopo.monteis.core.infrastructure.jooq;

import ch.swisstopo.monteis.core.infrastructure.security.ExperimentAccess;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisAuthenticationToken;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.ConnectionProvider;
import org.jooq.exception.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Writes the permissions of the {@link MonteisAuthenticationToken} bound to the current thread onto
 * each jOOQ connection as four Postgres GUCs, which the row-level security functions read via
 * {@code current_setting(...)} (V17):
 *
 * <ul>
 *   <li>{@code app.read_all_experiments} and {@code app.write_all_experiments}: {@code true} when
 *       the caller may read and write every experiment ({@link
 *       ExperimentAccess#isAdmin()});
 *   <li>{@code app.read_experiment_ids}: the readable experiment ids, comma-separated;
 *   <li>{@code app.write_experiment_ids}: the writable experiment ids, comma-separated, always a
 *       subset of the readable ones.
 * </ul>
 *
 * <p>They are transaction-local, not session-scoped, so they never leak to the next borrower of a
 * pooled connection: callers must run inside a Spring transaction. Any other or no authentication
 * fails closed (no flags, empty id lists) rather than throwing.
 */
public class RlsConnectionProvider implements ConnectionProvider {

  // IMPORTANT: set_config(..., true) scopes every value to the current transaction only!
  private static final String SET_RLS_CONTEXT =
      "SELECT set_config('app.read_all_experiments', ?, true),"
          + " set_config('app.write_all_experiments', ?, true),"
          + " set_config('app.read_experiment_ids', ?, true),"
          + " set_config('app.write_experiment_ids', ?, true)";

  private final ConnectionProvider delegate;

  public RlsConnectionProvider(ConnectionProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public Connection acquire() {
    Connection connection = delegate.acquire();
    writeRlsContext(connection, currentExperimentAccess());
    return connection;
  }

  @Override
  public void release(Connection connection) {
    delegate.release(connection);
  }

  private static ExperimentAccess currentExperimentAccess() {
    return SecurityContextHolder.getContext().getAuthentication()
            instanceof MonteisAuthenticationToken token
        ? token.experimentAccess()
        : ExperimentAccess.NONE;
  }

  private static void writeRlsContext(Connection connection, ExperimentAccess access) {
    try (PreparedStatement statement = connection.prepareStatement(SET_RLS_CONTEXT)) {
      statement.setString(1, String.valueOf(access.isAdmin()));
      statement.setString(2, String.valueOf(access.isAdmin()));
      statement.setString(3, toSortedCsv(access.readableExperimentIds()));
      statement.setString(4, toSortedCsv(access.writableExperimentIds()));
      statement.execute();
    } catch (SQLException e) {
      throw new DataAccessException("Failed to set RLS context", e);
    }
  }

  // sorted so that the same capabilities always produce the same setting value
  private static String toSortedCsv(Set<UUID> experimentIds) {
    return experimentIds.stream().sorted().map(UUID::toString).collect(Collectors.joining(","));
  }
}
