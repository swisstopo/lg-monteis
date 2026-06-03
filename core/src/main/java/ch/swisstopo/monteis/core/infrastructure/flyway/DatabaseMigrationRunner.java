package ch.swisstopo.monteis.core.infrastructure.flyway;

import ch.swisstopo.monteis.core.infrastructure.flyway.config.FdwPlaceholderProperties;
import ch.swisstopo.monteis.core.infrastructure.flyway.config.MetaMigrationProperties;
import ch.swisstopo.monteis.core.infrastructure.flyway.config.TimescaleMigrationProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!openapi")
public class DatabaseMigrationRunner {

  private final FdwPlaceholderProperties fdw;
  private final TimescaleMigrationProperties ts;
  private final MetaMigrationProperties meta;

  public DatabaseMigrationRunner(
      FdwPlaceholderProperties fdw, TimescaleMigrationProperties ts, MetaMigrationProperties meta) {
    this.fdw = fdw;
    this.ts = ts;
    this.meta = meta;
  }

  @PostConstruct
  public void executeMigrations() {
    Map<String, String> placeholders =
        Map.of(
            "fdw_ts_host", fdw.fdwTsHost(),
            "fdw_ts_port", fdw.fdwTsPort().toString(),
            "fdw_ts_dbname", fdw.fdwTsDbname(),
            "fdw_ts_user", fdw.fdwTsUser(),
            "fdw_ts_password", fdw.fdwTsPassword(),
            "fdw_app_user", fdw.fdwAppUserName());
    // Since the Metadata DB is the entrypoint for FDW we need to build the foreign tables first!
    runFlyway(ts.url(), ts.username(), ts.password(), ts.locations(), placeholders);

    // Migrate the Metadata DB second!
    runFlyway(meta.url(), meta.username(), meta.password(), meta.locations(), placeholders);
  }

  private void runFlyway(
      String url,
      String username,
      String password,
      String[] locations,
      Map<String, String> placeholders) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(username);
    config.setPassword(password);
    config.setMaximumPoolSize(2);
    config.setPoolName("Flyway-Migration-Pool-" + username);

    try (HikariDataSource migrationDataSource = new HikariDataSource(config)) {
      Flyway flyway =
          Flyway.configure()
              .dataSource(migrationDataSource)
              .locations(locations)
              .placeholders(placeholders)
              .load();

      flyway.migrate();
    }
  }
}
