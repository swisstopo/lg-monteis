package ch.swisstopo.monteis.core;

import ch.swisstopo.monteis.core.infrastructure.flyway.config.FdwPlaceholderProperties;
import ch.swisstopo.monteis.core.infrastructure.flyway.config.MetaMigrationProperties;
import ch.swisstopo.monteis.core.infrastructure.flyway.config.TimescaleMigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

  private final FdwPlaceholderProperties fdw;
  private final TimescaleMigrationProperties ts;
  private final MetaMigrationProperties meta;

  TestcontainersConfiguration(
      FdwPlaceholderProperties fdw, TimescaleMigrationProperties ts, MetaMigrationProperties meta) {
    this.fdw = fdw;
    this.ts = ts;
    this.meta = meta;
  }

  @Bean
  Network testNetwork() {
    return Network.newNetwork();
  }

  @Bean
  PostgreSQLContainer<?> timescaleDB(Network network) {

    return new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg18")
                .asCompatibleSubstituteFor("postgres"))
        .withNetwork(network)
        .withNetworkAliases(fdw.fdwTsHost())
        .withDatabaseName(ts.dbname())
        .withCopyFileToContainer(
            MountableFile.forHostPath("../docker/dataset-tsdb/init_tsdb.sql"),
            "/docker-entrypoint-initdb.d/init_tsdb.sql")
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("TIMESCALEDB"));
  }

  @Bean
  PostgreSQLContainer<?> metaDataDB(Network network, PostgreSQLContainer<?> timescaleDB) {

    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
        .withNetwork(network)
        .withDatabaseName(meta.dbname())
        .withCopyFileToContainer(
            MountableFile.forHostPath("../docker/dataset-meta/init_meta.sql"),
            "/docker-entrypoint-initdb.d/init_meta.sql")
        .dependsOn(timescaleDB)
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("METADB"));
  }

  @Bean
  DynamicPropertyRegistrar dynamicPropertyRegistrar(
      @Qualifier("metaDataDB") PostgreSQLContainer<?> meta,
      @Qualifier("timescaleDB") PostgreSQLContainer<?> ts) {
    return (registry) -> {
      registry.add("app.migration.meta.url", meta::getJdbcUrl);
      registry.add("app.migration.meta.username", meta::getUsername);
      registry.add("app.migration.meta.password", meta::getPassword);

      registry.add("app.migration.timescale.url", ts::getJdbcUrl);
      registry.add("app.migration.timescale.username", ts::getUsername);
      registry.add("app.migration.timescale.password", ts::getPassword);
    };
  }
}
