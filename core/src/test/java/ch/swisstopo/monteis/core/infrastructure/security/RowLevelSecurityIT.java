package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Sensors.SENSORS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.overview.jooq.OverviewQueryRepository;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end verification of row-level security against the dev seed data
 * ({@code db/meta/seed/R__seed_dev_data.sql}): sensor codes encode experiment membership after the
 * hyphen — experiment 1 is linked to sensors {@code TEMP-1} and {@code PRESS-1&2}; experiment 2 is
 * linked to {@code PRESS-1&2}, {@code DISP-2}, and {@code FLOW-2}; {@code FLOW-Admin} is linked to
 * no experiment at all, so only admins can ever see it. Readings
 * ({@code db/timescale/seed/R__seed_dev_data.sql}) exist for every seeded sensor. Users must only
 * see sensors and readings linked to an experiment on their (mocked) JWT; admins see everything;
 * an unbound context must fail closed.
 */
@IT
class RowLevelSecurityIT {

  @Autowired private DSLContext dsl;

  @Autowired private OverviewQueryRepository overviewQueryRepository;

  @Test
  @Transactional
  void user_scoped_to_experiment_one_sees_only_its_sensors_and_readings() {
    SecurityContextTestSupport.runAsUser(
        List.of(1L),
        () -> {
          assertEquals(2, dsl.fetchCount(SENSORS), "Experiment 1 has exactly 2 linked sensors");
          assertEquals(Set.of("TEMP-1", "PRESS-1&2"), fetchVisibleSensorCodes());
        });
  }

  @Test
  @Transactional
  void user_scoped_to_experiment_two_sees_only_its_sensors_and_readings() {
    SecurityContextTestSupport.runAsUser(
        List.of(2L),
        () -> {
          assertEquals(3, dsl.fetchCount(SENSORS), "Experiment 2 has exactly 3 linked sensors");
          assertEquals(Set.of("PRESS-1&2", "DISP-2", "FLOW-2"), fetchVisibleSensorCodes());
        });
  }

  @Test
  @Transactional
  void user_with_no_experiment_membership_sees_nothing() {
    SecurityContextTestSupport.runAsUser(
        List.of(),
        () -> {
          assertEquals(0, dsl.fetchCount(SENSORS));
          assertEquals(0, dsl.fetchCount(EXPERIMENTS));
          assertEquals(Set.of(), fetchVisibleSensorCodes());
        });
  }

  @Test
  @Transactional
  void admin_sees_every_sensor_experiment_and_reading() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          assertEquals(5, dsl.fetchCount(SENSORS), "Admin should see all seeded sensors");
          assertEquals(2, dsl.fetchCount(EXPERIMENTS), "Admin should see all seeded experiments");
          assertEquals(
              Set.of("TEMP-1", "PRESS-1&2", "DISP-2", "FLOW-2", "FLOW-Admin"),
              fetchVisibleSensorCodes(),
              "Admin should see every seeded reading's sensor code");
        });
  }

  @Test
  @Transactional
  void unbound_security_context_fails_closed() {
    // No SecurityContextTestSupport wrapping at all — the fail-closed default applies.
    assertEquals(0, dsl.fetchCount(SENSORS), "Unbound context must see zero sensors");
    assertEquals(0, dsl.fetchCount(EXPERIMENTS), "Unbound context must see zero experiments");
    assertEquals(Set.of(), fetchVisibleSensorCodes(), "Unbound context must see zero readings");
  }

  private Set<String> fetchVisibleSensorCodes() {
    List<ReadSimpleMetricDto> readings = overviewQueryRepository.fetchRecentMetrics(1000);
    return readings.stream().map(ReadSimpleMetricDto::sensorId).collect(Collectors.toSet());
  }
}
