package ch.swisstopo.monteis.core.infrastructure.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

/**
 * Selects the DataSource credential provider by active Spring profile.
 *
 * <p><b>Cloud (prod profile):</b> generates a short-lived RDS IAM authentication token via the
 * AWS SDK {@link RdsUtilities#generateAuthenticationToken} on every connection. The token is
 * derived from the Pod Identity credentials automatically available in the EKS pod's environment.
 * No static database password is provisioned or read in this path (FORBIDDEN by project rules).
 *
 * <p><b>Local (non-prod profiles):</b> standard username/password datasource backed by
 * {@code CORE_APP_META_DB_USER} and {@code CORE_APP_META_DB_PWD} environment variables. These
 * variables must NOT be provisioned in the cloud environment.
 *
 * <p>Both beans fail fast if their required credential source is absent: the cloud bean throws
 * at startup if Pod Identity credentials are unavailable; the local bean requires both env vars.
 */
@Configuration
public class DataSourceConfig {

  private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

  /**
   * Cloud datasource (prod profile). Connects to RDS using an IAM authentication token generated
   * per connection. Token expiry is 15 minutes; Hikari refreshes connections automatically.
   *
   * <p>The {@link RdsUtilities} client picks up the pod's Pod Identity credentials from the
   * standard AWS credential provider chain. The datasource fails to start if no credentials are
   * available (fail-fast, no silent fallback).
   */
  @Bean
  @Profile("prod")
  public DataSource iamAuthDataSource(
      @Value("${spring.datasource.url}") String jdbcUrl,
      @Value("${metadata.db.host}") String host,
      @Value("${metadata.db.port}") Integer port,
      @Value("${spring.datasource.username}") String username,
      @Value("${cloud.aws.region.static:eu-central-1}") String awsRegion) {

    // Validate that Pod Identity credentials are reachable before building the pool.
    // RdsUtilities.generateAuthenticationToken fetches credentials from the default chain,
    // which in EKS resolves to the Pod Identity agent. If the agent is unreachable the
    // AWS SDK throws immediately — this surfaces as a descriptive startup failure.
    RdsUtilities rdsUtilities = RdsUtilities.builder().region(Region.of(awsRegion)).build();

    // Eagerly generate one token to validate the credential chain at startup.
    // If this fails the application context will not start (fail-fast).
    String initialToken = generateToken(rdsUtilities, host, port, username);
    logger.info(
        "DataSourceConfig (prod): RDS IAM auth token generated successfully for host={}, user={}",
        host,
        username);

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(username);
    config.setPassword(initialToken);
    // Hikari will validate + refresh connections; token lifetime is 15 min.
    // maxLifetime < 15 min ensures a fresh token is always generated before expiry.
    config.setMaxLifetime(Duration.ofMinutes(13).toMillis());
    config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());

    // The wrapper supplies a fresh IAM token on each new physical connection (see
    // RdsIamHikariDataSource#getConnection); the eager token above only validated the
    // credential chain at startup. Clear it here so the wrapper is the sole token source.
    config.setPassword(null);
    return new RdsIamHikariDataSource(config, rdsUtilities, host, port, username);
  }

  /**
   * Local datasource (all non-prod profiles). Standard username/password authentication.
   *
   * <p>Reads {@code CORE_APP_META_DB_USER} and {@code CORE_APP_META_DB_PWD} from environment.
   * These variables must NOT be provisioned in cloud (FORBIDDEN by project rules).
   */
  @Bean
  @Profile("!prod")
  public DataSource localDataSource(
      @Value("${spring.datasource.url}") String jdbcUrl,
      @Value("${spring.datasource.username}") String username,
      @Value("${CORE_APP_META_DB_PWD}") String password) {

    logger.info("DataSourceConfig (local): using username/password auth for user={}", username);

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(username);
    config.setPassword(password);
    config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
    return new HikariDataSource(config);
  }

  private static String generateToken(
      RdsUtilities rdsUtilities, String hostname, int port, String username) {
    return rdsUtilities.generateAuthenticationToken(
        GenerateAuthenticationTokenRequest.builder()
            .hostname(hostname)
            .port(port)
            .username(username)
            .build());
  }
}
