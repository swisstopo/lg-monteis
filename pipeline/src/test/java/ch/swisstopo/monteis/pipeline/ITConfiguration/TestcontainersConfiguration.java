package ch.swisstopo.monteis.pipeline.ITConfiguration;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
  private static final Logger log = LoggerFactory.getLogger(TestcontainersConfiguration.class);

  @Bean
  Network testNetwork() {
    return Network.newNetwork();
  }

  @Bean
  PostgreSQLContainer<?> timescaleDB(@Value("${ts.db.name}") String dbName, Network network) {
    String monteisRoot = System.getProperty("monteis.repo.root", "..");
    return new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg18")
                .asCompatibleSubstituteFor("postgres"))
        .withNetwork(network)
        .withNetworkAliases("ts_db") // needed for fdw
        .withDatabaseName(dbName)
        .withCopyFileToContainer(
            MountableFile.forHostPath(monteisRoot + "/docker/dataset-tsdb/init_tsdb.sql"),
            "/docker-entrypoint-initdb.d/init_tsdb.sql")
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("TIMESCALEDB"));
  }

  @Bean
  DatabaseMigrations timeScaleMigrations(
      @Qualifier("timescaleDB") PostgreSQLContainer<?> timescaleDB,
      @Value("${ts.flyway.user.name}") String flywayUserName,
      @Value("${ts.flyway.user.pwd}") String flywayUserPwd,
      @Value("${fdw.read.user}") String fdwReadUser) {
    String monteisRoot = System.getProperty("monteis.repo.root", "..");
    log.info("Migrating Timescale DB...");
    Flyway.configure()
        .dataSource(timescaleDB.getJdbcUrl(), flywayUserName, flywayUserPwd)
        .locations("filesystem:" + monteisRoot + "/db/timescale/schema")
        .placeholders(
            Map.of(
                "fdw_read_user", fdwReadUser)) // let the fdw read user be controlled from outside
        .load()
        .migrate();
    return new DatabaseMigrations();
  }

  static final class DatabaseMigrations {}

  @Bean
  DynamicPropertyRegistrar dynamicPropertyRegistrar(
      @Qualifier("timescaleDB") PostgreSQLContainer<?> tsDb) {
    return (registry) -> {
      registry.add("spring.datasource.url", tsDb::getJdbcUrl);
    };
  }
}
