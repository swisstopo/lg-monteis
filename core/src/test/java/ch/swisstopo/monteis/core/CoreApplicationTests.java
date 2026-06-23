package ch.swisstopo.monteis.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.swisstopo.monteis.core.modules.demo.jooq.DemoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.testcontainers.containers.PostgreSQLContainer;

@IT
class CoreApplicationTests {

  @Autowired
  @Qualifier("metaDataDB")
  PostgreSQLContainer<?> postgres;

  @Autowired DemoRepository repo;

  @DisplayName("Should establish DB connection")
  @Test
  void shouldEstablishConnection() {
    assertThat(postgres.isRunning()).isTrue();
  }

  @DisplayName("Query Object from Timescale DB using FDW")
  @Test
  void shouldFindRecords() {
    assertEquals(3, (long) repo.fetchRecentMetrics(3).size());
  }
}