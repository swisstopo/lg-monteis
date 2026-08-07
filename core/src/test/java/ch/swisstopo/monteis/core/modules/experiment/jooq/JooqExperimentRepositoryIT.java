package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.ExperimentSensor.EXPERIMENT_SENSOR;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static org.junit.jupiter.api.Assertions.*;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.jooq.generated.enums.Status;
import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDates;
import ch.swisstopo.monteis.core.modules.experiment.web.dto.outbound.ExperimentResponseDto;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * All test bodies run as admin ({@link SecurityContextTestSupport#runAsAdmin}) so that
 * repository methods aren't filtered by row-level security — these tests exercise the read/aggregation,
 * CRUD, and auditing logic itself, not RLS. See {@code RowLevelSecurityIT} for user-scoped filtering coverage.
 */
@IT
class JooqExperimentRepositoryIT {
  @Autowired private JooqExperimentRepository repository;

  @Autowired private DSLContext dsl;

  @Autowired private SensorRepository sensorRepository;

  @Autowired private Javers javers;

  // --- Existing Read & Aggregation Tests ---

  @Test
  @Transactional
  void should_return_experiment_details_without_sensors() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Long experimentId =
              createExperimentWithDsl(
                  "Experiment Without Sensors",
                  "No sensors attached",
                  LocalDate.of(2024, Month.JANUARY, 1),
                  LocalDate.of(2024, Month.DECEMBER, 31));

          // Act
          ExperimentResponseDto details = repository.getById(experimentId);

          // Assert
          assertEquals(experimentId, details.id());
          assertEquals("Experiment Without Sensors", details.name());
          assertEquals("No sensors attached", details.comment());
        });
  }

  @Test
  @Transactional
  void should_return_experiment_details_with_linked_sensors_and_formulas() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Long experimentId =
              createExperimentWithDsl(
                  "Experiment With Sensors",
                  "Has 2 sensors",
                  LocalDate.of(2024, Month.JANUARY, 1),
                  LocalDate.of(2024, Month.DECEMBER, 31));

          Sensor sensor1 = createDummySensor("SENS-EXP-01", "Sensor One", "x * 2");
          Sensor sensor2 = createDummySensor("SENS-EXP-02", "Sensor Two", "x + 5");
          linkSensorToExperiment(experimentId, sensor1.getId());
          linkSensorToExperiment(experimentId, sensor2.getId());

          // Act
          ExperimentResponseDto details = repository.getById(experimentId);

          // Assert
          assertEquals(2, details.sensorCount());
        });
  }

  @Test
  @Transactional
  void should_return_null_for_nonexistent_experiment() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act
          ExperimentResponseDto details = repository.getById(999999L);

          // Assert
          assertNull(details, "Non-existent experiment should resolve to null");
        });
  }

  // --- New CRUD & Auditing Tests (Mirroring Sensor IT) ---

  @Test
  @Transactional
  void should_create_experiment() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          int initialExperimentCount = dsl.fetchCount(EXPERIMENTS);
          Experiment newExperiment = buildDummyDomainExperiment("EXP-CREATE-001", "Admin");

          // Act
          Experiment savedExperiment = repository.create(newExperiment);

          // Assert
          assertAll(
              () ->
                  assertNotNull(
                      savedExperiment.getId(), "Experiment ID should not be null after insert"),
              () -> assertEquals("EXP-CREATE-001", savedExperiment.getName()),
              () -> assertEquals("Dummy comment", savedExperiment.getComment()));

          // Verify DB state directly: Assert the DELTA
          assertEquals(
              initialExperimentCount + 1,
              dsl.fetchCount(EXPERIMENTS),
              "Experiment count should increase by exactly 1");

          // Ultimate DB verification: Prove the exact record exists in the physical table
          boolean existsInDb =
              dsl.fetchExists(
                  dsl.selectFrom(EXPERIMENTS).where(EXPERIMENTS.ID.eq(savedExperiment.getId())));
          assertTrue(
              existsInDb, "The newly created experiment must physically exist in the database");
        });
  }

  @Test
  @Transactional
  void should_throw_on_create_non_unique_experiment() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Experiment uniqueExperiment = buildDummyDomainExperiment("Uniqe_Experiment", "Owner_1");
          Experiment nonUniqueExperiment =
              buildDummyDomainExperiment("Uniqe_Experiment", "Owner_1");
          repository.create(uniqueExperiment);

          // Act & Assert
          FieldBusinessValidationException exception =
              assertThrows(
                  FieldBusinessValidationException.class,
                  () -> repository.create(nonUniqueExperiment));

          assertEquals("name", exception.getField());
          assertEquals("validation.unique", exception.getMessageKey());
        });
  }

  @Test
  @Transactional
  void should_update_experiment() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Experiment savedExperiment =
              repository.create(buildDummyDomainExperiment("OLD-NAME", "Old Owner"));

          // Mutate domain object
          savedExperiment.setName("NEW-NAME");
          savedExperiment.setOwner("New Owner");
          savedExperiment.setComment("Updated comment");

          // Act
          Experiment updatedExperiment = repository.update(savedExperiment);

          // Assert
          assertEquals("NEW-NAME", updatedExperiment.getName());
          assertEquals("Updated comment", updatedExperiment.getComment());
        });
  }

  @Test
  @Transactional
  void should_throw_on_update_duplicated_code() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Experiment uniqueExperiment1 =
              buildDummyDomainExperiment("Unique_Experiment1", "Owner_1");
          Experiment uniqueExperiment2 =
              buildDummyDomainExperiment("Unique_Experiment2", "Owner_1");
          uniqueExperiment1 = repository.create(uniqueExperiment1);
          uniqueExperiment2 = repository.create(uniqueExperiment2);

          uniqueExperiment2.setName(uniqueExperiment1.getName());

          // Act & Assert
          Experiment nonUniqueExperiment = uniqueExperiment2;
          FieldBusinessValidationException exception =
              assertThrows(
                  FieldBusinessValidationException.class,
                  () -> repository.update(nonUniqueExperiment));

          assertEquals("name", exception.getField());
          assertEquals("validation.unique", exception.getMessageKey());
        });
  }

  @Test
  @Transactional
  void should_throw_on_update_deleted_experiment() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Experiment ghostExperiment = buildDummyDomainExperiment("GHOST-EXP", "Ghost Owner");
          ghostExperiment.setId(99999L);

          // Act & Assert
          ObjectBusinessValidationException exception =
              assertThrows(
                  ObjectBusinessValidationException.class,
                  () -> repository.update(ghostExperiment));

          assertEquals("object.deleted", exception.getMessageKey());
        });
  }

  @Test
  @Transactional
  void should_stream_unaudited_experiments() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          repository.create(buildDummyDomainExperiment("UNAUDITED-EXP-1", "Test Owner"));

          // Act
          try (Stream<Experiment> stream = repository.streamUnauditedExperiments()) {
            List<Experiment> unauditedExperiments = stream.toList();

            // Assert
            assertFalse(unauditedExperiments.isEmpty(), "Stream should not be empty");

            boolean containsOurExperiment =
                unauditedExperiments.stream().anyMatch(e -> e.getName().equals("UNAUDITED-EXP-1"));

            assertTrue(
                containsOurExperiment,
                "Stream should contain the newly created unaudited experiment");
          }
        });
  }

  @Test
  @Transactional
  void should_exclude_audited_experiments() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Experiment experiment =
              repository.create(buildDummyDomainExperiment("AUDITED-EXP-1", "Test Owner"));

          // Commit to Javers to simulate auditing
          javers.commit("TEST_AUTHOR", experiment);

          // Act
          try (Stream<Experiment> stream = repository.streamUnauditedExperiments()) {
            List<Experiment> unauditedExperiments = stream.toList();

            // Assert
            boolean containsOurExperiment =
                unauditedExperiments.stream().anyMatch(e -> e.getName().equals("AUDITED-EXP-1"));

            assertFalse(containsOurExperiment, "Stream should NOT contain the audited experiment");
          }
        });
  }

  // --- Helper Methods ---

  /**
   * Helper to quickly build a valid domain Experiment for repository testing.
   */
  private Experiment buildDummyDomainExperiment(String name, String owner) {
    ExperimentDates dates =
        new ExperimentDates(
            LocalDate.of(2024, Month.JANUARY, 1), LocalDate.of(2024, Month.DECEMBER, 31));

    return new Experiment(
        name,
        owner,
        dates,
        "Dummy comment",
        ch.swisstopo.monteis.core.modules.experiment.domain.Status.ACTIVE);
  }

  /**
   * Legacy helper using DSL context directly (used by read-only tests)
   */
  private Long createExperimentWithDsl(
      String name, String comment, LocalDate experimentStart, LocalDate experimentEnd) {
    return Objects.requireNonNull(
            dsl.insertInto(EXPERIMENTS)
                .set(EXPERIMENTS.NAME, name)
                .set(EXPERIMENTS.COMMENT, comment)
                .set(EXPERIMENTS.EXPERIMENT_START, experimentStart)
                .set(EXPERIMENTS.EXPERIMENT_END, experimentEnd)
                .set(EXPERIMENTS.STATUS, Status.ACTIVE.toString())
                .set(EXPERIMENTS.OWNER, "owner")
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
    AlarmLimits alarmLimits = new AlarmLimits(0.0, 100.0);
    Coordinates coordinates = new Coordinates(2400, -12007, -1600);

    return sensorRepository.create(
        new Sensor(
            code,
            name,
            new SensorType(null, "Other", null),
            Unit.METER,
            null,
            coordinates,
            alarmLimits,
            true,
            formula));
  }
}
