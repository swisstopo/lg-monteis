package ch.swisstopo.monteis.core.infrastructure.jooq;

import ch.swisstopo.monteis.core.infrastructure.security.AuthorityChecks;
import ch.swisstopo.monteis.core.infrastructure.security.MonteisPrincipal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.ConnectionProvider;
import org.jooq.exception.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Writes the current {@link Authentication} onto each jOOQ connection as transaction-local
 * Postgres GUCs ({@code app.read_all}, {@code app.user_experiment_ids}, {@code app.write_all},
 * {@code app.user_write_experiment_ids}), which RLS policies read
 * via {@code current_setting(...)}. Transaction-local, not session-scoped, so values never leak to
 * the next borrower of a pooled connection — callers must run inside a Spring transaction. An
 * unbound or unrecognized {@code Authentication} fails closed (no read-all, no experiment ids)
 * rather than throwing.
 */
public class RlsConnectionProvider implements ConnectionProvider {

  // IMPORTANT: use set_config in order to have config for the current transaction only!
  private static final String SET_RLS_CONTEXT =
      "SELECT set_config('app.read_all', ?, true), set_config('app.user_experiment_ids', ?, true),"
          + " set_config('app.write_all', ?, true),"
          + " set_config('app.user_write_experiment_ids', ?, true)";

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

  private static void applySecurityContext(Connection connection) {
    RlsContext context = RlsContext.of(SecurityContextHolder.getContext().getAuthentication());
    try (PreparedStatement statement = connection.prepareStatement(SET_RLS_CONTEXT)) {
      statement.setString(1, String.valueOf(context.readAll()));
      statement.setString(2, toCsv(context.readExperimentIds()));
      statement.setString(3, String.valueOf(context.writeAll()));
      statement.setString(4, toCsv(context.writeExperimentIds()));
      statement.execute();
    } catch (SQLException e) {
      throw new DataAccessException("Failed to set RLS context", e);
    }
  }

  private static String toCsv(List<UUID> experimentIds) {
    return experimentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  /**
   * What the RLS policies may see of the caller. An {@code *All} flag makes the matching id list
   * irrelevant, so it is left empty.
   */
  private record RlsContext(
      boolean readAll,
      List<UUID> readExperimentIds,
      boolean writeAll,
      List<UUID> writeExperimentIds) {

    private static final RlsContext NONE = new RlsContext(false, List.of(), false, List.of());

    static RlsContext of(Authentication authentication) {
      if (authentication == null) {
        return NONE;
      }
      boolean readAll = AuthorityChecks.canReadAllExperiments(authentication);
      boolean writeAll = AuthorityChecks.canWriteAllExperiments(authentication);
      if (!(authentication.getPrincipal() instanceof MonteisPrincipal principal)) {
        return new RlsContext(readAll, List.of(), writeAll, List.of());
      }
      return new RlsContext(
          readAll,
          readAll ? List.of() : principal.getReadExperimentIds(),
          writeAll,
          writeAll ? List.of() : principal.getWriteExperimentIds());
    }
  }
}
