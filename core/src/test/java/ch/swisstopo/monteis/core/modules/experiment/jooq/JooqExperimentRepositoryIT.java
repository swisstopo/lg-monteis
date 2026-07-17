package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.ExperimentSensor.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.ITConfiguration.IT;
import ch.swisstopo.monteis.core.ITConfiguration.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.ReadExperimentDetailsDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.Bounds;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.domain.SensorRepository;
import java.util.Objects;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * All test bodies run as admin ({@link SecurityContextTestSupport#runAsAdmin}) so that
 * {@code repository.getExperimentDetails(...)} isn't filtered by row-level security — these
 * tests exercise the read/aggregation logic itself, not RLS. See {@code RowLevelSecurityIT} for
 * user-scoped filtering coverage.
 */
@IT
class JooqExperimentRepositoryIT {
  @Autowired private JooqExperimentRepository repository;

  @Autowired private DSLContext dsl;

  @Autowired private SensorRepository sensorRepository;

  @Test
  @Transactional
  void should_return_experiment_details_without_sensors() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Long experimentId = createExperiment("Experiment Without Sensors", "No sensors attached");

          // Act
          ReadExperimentDetailsDto details = repository.getExperimentDetails(experimentId);

          // Assert
          assertEquals(experimentId, details.id());
          assertEquals("Experiment Without Sensors", details.name());
          assertEquals("No sensors attached", details.description());
          assertEquals(1, details.version());
          assertTrue(details.sensors().isEmpty(), "Sensors list should be empty");
        });
  }

  @Test
  @Transactional
  void should_return_experiment_details_with_linked_sensors_and_formulas() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Long experimentId = createExperiment("Experiment With Sensors", "Has two sensors");

          Sensor sensor1 = createDummySensor("SENS-EXP-01", "Sensor One", "x * 2");
          Sensor sensor2 = createDummySensor("SENS-EXP-02", "Sensor Two", "x + 5");
          linkSensorToExperiment(experimentId, sensor1.getId());
          linkSensorToExperiment(experimentId, sensor2.getId());

          // Act
          ReadExperimentDetailsDto details = repository.getExperimentDetails(experimentId);

          // Assert
          assertEquals(2, details.sensors().size());
          assertTrue(
              details.sensors().stream()
                  .anyMatch(
                      s ->
                          s.code().equals("SENS-EXP-01")
                              && s.formula().expression().equals("x * 2")),
              "Sensor One with its formula should be present");
          assertTrue(
              details.sensors().stream()
                  .anyMatch(
                      s ->
                          s.code().equals("SENS-EXP-02")
                              && s.formula().expression().equals("x + 5")),
              "Sensor Two with its formula should be present");
        });
  }

  @Test
  @Transactional
  void should_return_null_for_nonexistent_experiment() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          ReadExperimentDetailsDto details = repository.getExperimentDetails(999999L);

          // Assert
          assertNull(details, "Non-existent experiment should resolve to null");
        });
  }

  // --- Helper Methods ---

  private Long createExperiment(String name, String description) {
    return Objects.requireNonNull(
            dsl.insertInto(EXPERIMENTS)
                .set(EXPERIMENTS.NAME, name)
                .set(EXPERIMENTS.DESCRIPTION, description)
                .returning(EXPERIMENTS.ID)
                .fetchOne())
        .getId();
  }

  private void linkSensorToExperiment(Long experimentId, Long sensorId) {
    dsl.insertInto(EXPERIMENT_SENSOR)
        .set(EXPERIMENT_SENSOR.EXPERIMENT_ID, experimentId)
        .set(EXPERIMENT_SENSOR.SENSOR_ID, sensorId)
        .execute();
  }

  /**
   * Helper to quickly build a valid, persisted Sensor for linking to an experiment.
   */
  private Sensor createDummySensor(String code, String name, String formulaExpression) {
    Formula formula = new Formula();
    formula.setExpression(formulaExpression);
    Bounds bounds = new Bounds(0.0, 100.0);

    return sensorRepository.create(new Sensor(code, name, bounds, formula));
  }
}
