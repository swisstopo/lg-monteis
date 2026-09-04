package ch.swisstopo.monteis.core.infrastructure.security;

import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Sensors.SENSORS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.jooq.generated.routines.CanAccessExperiment;
import ch.swisstopo.monteis.core.jooq.generated.routines.CanAccessSensor;
import ch.swisstopo.monteis.core.modules.overview.jooq.OverviewQueryRepository;
import ch.swisstopo.monteis.core.modules.overview.web.dto.ReadSimpleMetricDto;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

  // Fixed IDs from db/meta/seed/R__seed_dev_data.sql: "Mont Terri Alpha" and "Mont Terri Beta".
  private static final UUID EXPERIMENT_ALPHA =
      UUID.fromString("00000000-0000-7000-8000-000000000301");
  private static final UUID EXPERIMENT_BETA =
      UUID.fromString("00000000-0000-7000-8000-000000000302");

  @Autowired private DSLContext dsl;

  @Autowired private OverviewQueryRepository overviewQueryRepository;

  @Test
  @Transactional
  void user_scoped_to_experiment_one_sees_only_its_sensors_and_readings() {
    SecurityContextTestSupport.runAsUser(
        List.of(EXPERIMENT_ALPHA),
        () -> {
          assertEquals(12, dsl.fetchCount(SENSORS), "Experiment 1 has exactly 2 linked sensors");
          assertEquals(Set.of("TEMP-1", "PRESS-1&2"), fetchVisibleSensorCodes());
        });
  }

  @Test
  @Transactional
  void user_scoped_to_experiment_two_sees_only_its_sensors_and_readings() {
    SecurityContextTestSupport.runAsUser(
        List.of(EXPERIMENT_BETA),
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
          assertEquals(15, dsl.fetchCount(SENSORS), "Admin should see all seeded sensors");
          assertEquals(8, dsl.fetchCount(EXPERIMENTS), "Admin should see all seeded experiments");
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

  @Test
  @Transactional
  void can_access_functions_do_not_error_on_a_null_target_id() {
    // A null sensor/experiment id can never actually reach these functions in practice (ids are
    // NOT NULL columns), but the RLS policies call them for every row, so a null argument must
    // resolve to "no access" rather than raise a SQL error and take the whole query down with it.
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // can_read_all() short-circuits the OR, so a null target id is irrelevant for admins.
          assertEquals(Boolean.TRUE, callCanAccessSensor(null));
          assertEquals(Boolean.TRUE, callCanAccessExperiment(null));
        });

    SecurityContextTestSupport.runAsUser(
        List.of(EXPERIMENT_ALPHA),
        () -> {
          // Non-admin: "target_id = ANY(...)" with a null target evaluates to SQL NULL, not
          // TRUE - never grants access, and critically never throws.
          Boolean sensorAccess = callCanAccessSensor(null);
          Boolean experimentAccess = callCanAccessExperiment(null);
          assertNotEquals(Boolean.TRUE, sensorAccess);
          assertNotEquals(Boolean.TRUE, experimentAccess);
        });
  }

  private Boolean callCanAccessSensor(UUID targetSensorId) {
    CanAccessSensor routine = new CanAccessSensor();
    routine.setTargetSensorId(targetSensorId);
    routine.execute(dsl.configuration());
    return routine.getReturnValue();
  }

  private Boolean callCanAccessExperiment(UUID targetExperimentId) {
    CanAccessExperiment routine = new CanAccessExperiment();
    routine.setTargetExperimentId(targetExperimentId);
    routine.execute(dsl.configuration());
    return routine.getReturnValue();
  }

  private Set<String> fetchVisibleSensorCodes() {
    List<ReadSimpleMetricDto> readings = overviewQueryRepository.fetchRecentMetrics(1000);
    return readings.stream().map(ReadSimpleMetricDto::sensorId).collect(Collectors.toSet());
  }
}
