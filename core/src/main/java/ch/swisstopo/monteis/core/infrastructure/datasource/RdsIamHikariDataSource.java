package ch.swisstopo.monteis.core.infrastructure.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

/**
 * A {@link HikariDataSource} extension that generates a fresh RDS IAM auth token on every new
 * physical connection. This ensures the JDBC password is never an expired IAM token (tokens are
 * valid for 15 minutes; connections are rotated by maxLifetime before expiry).
 *
 * <p>The token is generated synchronously when Hikari creates a connection. If the AWS SDK cannot
 * obtain Pod Identity credentials the exception propagates to Hikari, which surfaces it as a
 * connection-pool failure (fail-fast, no silent fallback).
 *
 * <p><b>Thread safety:</b> {@link HikariDataSource#setPassword} mutates shared state on the
 * underlying config; calling it unsynchronized from concurrent Hikari threads causes
 * cross-contamination (one thread's token overwrites another's before {@code super.getConnection}
 * reads it). The fix synchronises the generate-and-set-password critical section on {@code this}
 * so that each thread sees the token it generated. The lock is per-datasource (one cloud instance),
 * so contention is bounded and brief (one AWS SDK call under the lock).
 */
class RdsIamHikariDataSource extends HikariDataSource {

  private final RdsUtilities rdsUtilities;
  private final String hostname;
  private final int port;
  private final String username;

  RdsIamHikariDataSource(
      HikariConfig config, RdsUtilities rdsUtilities, String hostname, int port, String username) {
    super(config);
    this.rdsUtilities = rdsUtilities;
    this.hostname = hostname;
    this.port = port;
    this.username = username;
  }

  /**
   * Generates a fresh IAM auth token and opens a physical connection atomically.
   *
   * <p>Synchronised on {@code this}: the generate-token → setPassword → getConnection sequence
   * must be atomic. Without synchronisation, thread A's {@code setPassword(tokenA)} can be
   * overwritten by thread B's {@code setPassword(tokenB)} before A calls
   * {@code super.getConnection()}, causing A to connect with B's token (or vice versa). Both
   * tokens are valid (15-minute lifetime), but the race is a correctness defect — the pool's
   * internal config state is shared and not thread-safe for concurrent mutation.
   */
  @Override
  public synchronized Connection getConnection() throws SQLException {
    // Refresh password with a fresh token before each new physical connection.
    // Hikari calls this via the connection factory; the token is valid for >=2 minutes
    // past maxLifetime so expiry during the connection lifetime is not a risk.
    String token =
        rdsUtilities.generateAuthenticationToken(
            GenerateAuthenticationTokenRequest.builder()
                .hostname(hostname)
                .port(port)
                .username(username)
                .build());
    setPassword(token);
    return super.getConnection();
  }
}
