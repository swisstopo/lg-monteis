package ch.swisstopo.monteis.core;

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
  PostgreSQLContainer<?> timescaleDB(
      Network network,
      @Value("${app.migration.meta.placeholders.fdw_ts_host}") String fdwTsHost,
      @Value("${app.migration.timescale.dbname}") String tsDbName) {

    return new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg18")
                .asCompatibleSubstituteFor("postgres"))
        .withNetwork(network)
        .withNetworkAliases(fdwTsHost)
        .withDatabaseName(tsDbName)
        .withCopyFileToContainer(
            MountableFile.forHostPath("../docker/dataset-tsdb/init_tsdb.sql"),
            "/docker-entrypoint-initdb.d/init_tsdb.sql")
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("TIMESCALEDB"));
  }

  @Bean
  PostgreSQLContainer<?> metaDataDB(
      Network network,
      PostgreSQLContainer<?> timescaleDB,
      @Value("${app.migration.meta.dbname}") String metaDbName) {

    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
        .withNetwork(network)
        .withDatabaseName(metaDbName)
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
      registry.add("spring.datasource.url", meta::getJdbcUrl);
      registry.add("app.migration.meta.url", meta::getJdbcUrl);
      registry.add("app.migration.timescale.url", ts::getJdbcUrl);
    };
  }
}
