package ch.swisstopo.monteis.core;

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
class TestcontainersConfiguration {
  private static final Logger log = LoggerFactory.getLogger(TestcontainersConfiguration.class);

  @Bean
  Network testNetwork() {
    return Network.newNetwork();
  }

  @Bean
  PostgreSQLContainer<?> timescaleDB(@Value("${ts.db.name}") String dbName, Network network) {

    return new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg18")
                .asCompatibleSubstituteFor("postgres"))
        .withNetwork(network)
        .withNetworkAliases("ts_db") // needed for fdw
        .withDatabaseName(dbName)
        .withCopyFileToContainer(
            MountableFile.forHostPath("../docker/dataset-tsdb/init_tsdb.sql"),
            "/docker-entrypoint-initdb.d/init_tsdb.sql")
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("TIMESCALEDB"));
  }

  @Bean
  DatabaseMigrations timeScaleMigrations(
      @Qualifier("timescaleDB") PostgreSQLContainer<?> timescaleDB,
      @Value("${ts.flyway.user.name}") String flywayUserName,
      @Value("${ts.flyway.user.pwd}") String flywayUserPwd,
      @Value("${fdw.read.user}") String fdwReadUser) {
    String migrationRoot = System.getProperty("db.migration.root", "..");
    log.info("Migrating Timescale DB...");
    Flyway.configure()
        .dataSource(timescaleDB.getJdbcUrl(), flywayUserName, flywayUserPwd)
        .locations(
            "filesystem:" + migrationRoot + "/db/timescale/schema",
            "filesystem:" + migrationRoot + "/db/timescale/seed")
        .placeholders(
            Map.of(
                "fdw_read_user", fdwReadUser)) // let the fdw read user be controlled from outside
        .load()
        .migrate();
    return new DatabaseMigrations();
  }

  static final class DatabaseMigrations {}

  @Bean
  PostgreSQLContainer<?> metaDataDB(
      Network network,
      PostgreSQLContainer<?> timescaleDB,
      @Value("${meta.db.name}") String dbName) {

    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
        .withNetwork(network)
        .withDatabaseName(dbName)
        .withCopyFileToContainer(
            MountableFile.forHostPath("../docker/dataset-meta/init_meta.sql"),
            "/docker-entrypoint-initdb.d/init_meta.sql")
        .dependsOn(timescaleDB)
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("METADB"));
  }

  @Bean
  DatabaseMigrations metaDataMigrations(
      @Qualifier("metaDataDB") PostgreSQLContainer<?> metaDataDB,
      @Qualifier("timeScaleMigrations")
          DatabaseMigrations
              tsDBMigrations, // enforce the Timescale migration to succeed first in order to have
      // correct permissions for fdw. todo: check if proper!!
      @Value("${meta.flyway.user.name}") String flywayUserName,
      @Value("${meta.flyway.user.pwd}") String flywayUserPwd) {
    String migrationRoot = System.getProperty("db.migration.root", "..");
    log.info("Migrating Metadata DB...");
    Flyway.configure()
        .dataSource(metaDataDB.getJdbcUrl(), flywayUserName, flywayUserPwd)
        .locations(
            "filesystem:" + migrationRoot + "/db/meta/schema",
            "filesystem:" + migrationRoot + "/db/meta/seed")
        .load()
        .migrate();
    return new DatabaseMigrations();
  }

  @Bean
  DynamicPropertyRegistrar dynamicPropertyRegistrar(
      @Qualifier("metaDataDB") PostgreSQLContainer<?> meta) {
    return (registry) -> {
      registry.add("spring.datasource.url", meta::getJdbcUrl);
    };
  }
}
