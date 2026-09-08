package ch.swisstopo.monteis.core.modules.sensor.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.FORMULAS;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Sensors.SENSORS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.infrastructure.exception.FieldBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.exception.ObjectBusinessValidationException;
import ch.swisstopo.monteis.core.infrastructure.query.NumberFilterModel;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.PagedResult;
import ch.swisstopo.monteis.core.infrastructure.query.SortDirection;
import ch.swisstopo.monteis.core.infrastructure.query.SortModelItem;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.sensor.domain.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * All test bodies run as admin ({@link SecurityContextTestSupport#runAsAdmin}) so that direct
 * reads of {@code sensors} (via {@code dsl}, {@code repository.update(...)}, and
 * {@code repository.streamUnauditedSensors()}) aren't filtered by row-level security — these
 * tests exercise sensor CRUD behavior, not RLS itself.
 */
@IT
class JooqSensorRepositoryIT {
  @Autowired private JooqSensorRepository repository;

  @Autowired private DSLContext dsl;

  @Autowired private Javers javers;

  @Test
  @Transactional
  void should_create_sensor_and_formula() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          int initialSensorCount = dsl.fetchCount(SENSORS);
          int initialFormulaCount = dsl.fetchCount(FORMULAS);
          Sensor newSensor = createDummySensor("SENS-001", "Test Sensor", "x * 2");

          // Act
          Sensor savedSensor = repository.create(newSensor);

          // Assert
          assertAll(
              () -> assertNotNull(savedSensor.getId(), "Sensor ID should not be null after insert"),
              () -> assertEquals("SENS-001", savedSensor.getCode()),
              () -> assertNotNull(savedSensor.getFormula(), "Formula should be mapped back"),
              () ->
                  assertNotNull(savedSensor.getFormula().getId(), "Formula ID should not be null"),
              () -> assertEquals("x * 2", savedSensor.getFormula().getExpression()));

          // Verify DB state directly: Assert the DELTA
          assertEquals(
              initialSensorCount + 1,
              dsl.fetchCount(SENSORS),
              "Sensor count should increase by exactly 1");
          assertEquals(
              initialFormulaCount + 1,
              dsl.fetchCount(FORMULAS),
              "Formula count should increase by exactly 1");

          // Ultimate DB verification: Prove the exact record exists in the physical table
          boolean existsInDb =
              dsl.fetchExists(dsl.selectFrom(SENSORS).where(SENSORS.ID.eq(savedSensor.getId())));
          assertTrue(existsInDb, "The newly created sensor must physically exist in the database");
        });
  }

  @Test
  @Transactional
  void should_filter_paged_sensors_by_text_contains() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          repository.create(createDummySensor("TXT-FILTER-01", "UniqueTextFilterName", "x"));
          repository.create(createDummySensor("TXT-FILTER-02", "Other Name", "x"));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "uniquetextfilter", null)));

          // Act
          PagedResult<Sensor> result = repository.findPaged(request);

          // Assert
          assertEquals(1, result.totalCount());
          assertEquals("TXT-FILTER-01", result.rows().getFirst().getCode());
        });
  }

  @Test
  @Transactional
  void should_sort_paged_sensors_by_text_column_descending() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          repository.create(createDummySensor("SORT-A", "AAA_SORT_TEST", "x"));
          repository.create(createDummySensor("SORT-Z", "ZZZ_SORT_TEST", "x"));

          // Scope to just our two sensors via a filter, so the sort assertion doesn't depend on
          // how many other rows happen to be seeded ahead of a fixed-size page.
          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(new SortModelItem("name", SortDirection.DESC)),
                  Map.of("name", new TextFilterModel("contains", "_SORT_TEST", null)));

          // Act
          List<Sensor> rows = repository.findPaged(request).rows();

          // Assert: among our two sensors, the Z one must come first in descending order
          int indexOfZ = indexOfCode(rows, "SORT-Z");
          int indexOfA = indexOfCode(rows, "SORT-A");
          assertTrue(
              indexOfZ < indexOfA, "ZZZ_SORT_TEST should sort before AAA_SORT_TEST in DESC order");
        });
  }

  @Test
  @Transactional
  void should_filter_paged_sensors_by_number_range() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange: coordinates.x is a jOOQ Integer field, filtered via a Double-typed
          // NumberFilterModel - this exercises the numeric CAST rather than an unsafe cast.
          repository.create(
              createDummySensorWithCoordinates(
                  "NUM-FILTER-01", "Number Filter Sensor", "x", new Coordinates(123456789, 0, 0)));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "coordinates.x", new NumberFilterModel("inRange", 123456788.0, 123456790.0)));

          // Act
          PagedResult<Sensor> result = repository.findPaged(request);

          // Assert
          assertEquals(1, result.totalCount());
          assertEquals("NUM-FILTER-01", result.rows().getFirst().getCode());
        });
  }

  @Test
  @Transactional
  void should_reuse_existing_formula() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Sensor sensor1 = createDummySensor("SENS-001", "Sensor 1", "x * 2");
          Sensor sensor2 = createDummySensor("SENS-002", "Sensor 2", "x * 2");

          // Capture initial count BEFORE the second insert
          Sensor saved1 = repository.create(sensor1);
          int sensorCountAfterFirst = dsl.fetchCount(SENSORS);
          int formulaCountAfterFirst = dsl.fetchCount(FORMULAS);

          // Act
          Sensor saved2 = repository.create(sensor2);

          // Assert
          assertEquals(
              saved1.getFormula().getId(),
              saved2.getFormula().getId(),
              "Both sensors should reference the exact same formula ID");

          // Verify DB state: Sensor increased, but formula stayed exactly the same
          assertEquals(
              sensorCountAfterFirst + 1,
              dsl.fetchCount(SENSORS),
              "A second sensor should be inserted");
          assertEquals(
              formulaCountAfterFirst,
              dsl.fetchCount(FORMULAS),
              "No new formula should be inserted");
        });
  }

  @Test
  @Transactional
  void should_throw_on_duplicate_code() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Sensor sensor1 = createDummySensor("DUPE-CODE", "Sensor 1", "x");
          Sensor sensor2 = createDummySensor("DUPE-CODE", "Sensor 2", "x+y");
          repository.create(sensor1);

          // Act & Assert
          FieldBusinessValidationException exception =
              assertThrows(
                  FieldBusinessValidationException.class, () -> repository.create(sensor2));

          assertEquals("code", exception.getField());
          assertEquals("validation.unique", exception.getMessageKey());
        });
  }

  @Test
  @Transactional
  void should_update_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Sensor savedSensor = repository.create(createDummySensor("UPD-001", "Old Name", "x"));

          // Mutate domain object
          savedSensor.setName("New Name");
          savedSensor.getFormula().setExpression("x * 3");

          // Act
          Sensor updatedSensor = repository.update(savedSensor);

          // Assert
          assertEquals("New Name", updatedSensor.getName());
          assertEquals("x * 3", updatedSensor.getFormula().getExpression());
        });
  }

  @Test
  @Transactional
  void should_find_sensor_by_id() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Sensor savedSensor =
              repository.create(createDummySensor("FIND-001", "Find Sensor", "x * 2"));

          // Act
          Optional<Sensor> found = repository.findById(savedSensor.getId());

          // Assert
          assertTrue(found.isPresent());
          assertEquals("FIND-001", found.get().getCode());
          assertEquals("x * 2", found.get().getFormula().getExpression());
          assertEquals(0.0, found.get().getAlarmLimits().lower());
          assertEquals(100.0, found.get().getAlarmLimits().upper());
          assertEquals("Other", found.get().getType().name());
        });
  }

  @Test
  @Transactional
  void should_return_empty_when_sensor_not_found_by_id() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Act & Assert
          assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
        });
  }

  @Test
  @Transactional
  void should_throw_on_update_deleted_sensor() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Sensor ghostSensor = createDummySensor("GHOST", "Ghost", "x");
          ghostSensor.setId(UUID.randomUUID());

          // Act & Assert
          ObjectBusinessValidationException exception =
              assertThrows(
                  ObjectBusinessValidationException.class, () -> repository.update(ghostSensor));

          assertEquals("object.deleted", exception.getMessageKey());
        });
  }

  @Test
  @Transactional
  void should_throw_on_update_duplicated_code() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Sensor sensor1 = createDummySensor("UPD-DUPE-CODE", "Sensor 1", "x");
          Sensor sensor2 = createDummySensor("UPD-NO-DUPE-CODE", "Sensor 2", "x+y");
          sensor1 = repository.create(sensor1);
          sensor2 = repository.create(sensor2);

          sensor2.setCode(sensor1.getCode());

          // Act & Assert
          Sensor finalSensor = sensor2;
          FieldBusinessValidationException exception =
              assertThrows(
                  FieldBusinessValidationException.class, () -> repository.update(finalSensor));

          assertEquals("code", exception.getField());
          assertEquals("validation.unique", exception.getMessageKey());
        });
  }

  @Test
  @Transactional
  void should_find_all_formulas_ordered() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          repository.create(createDummySensor("S-01", "A", "a * x"));
          repository.create(createDummySensor("S-02", "B", "b * x"));
          repository.create(createDummySensor("S-03", "C", "c * x"));

          // Act
          List<Formula> formulas = repository.findAllFormulas();

          // Assert
          assertEquals("a * x", formulas.get(0).getExpression());
          assertEquals("b * x", formulas.get(1).getExpression());
          assertEquals("c * x", formulas.get(2).getExpression());
        });
  }

  @Test
  @Transactional
  void should_find_all_types_ordered() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange: use seeded values
          //    (1, 'Temperature', 1),
          //    (2, 'Stress Radial', 1),
          //    (3, 'Other', 1),
          //    (4, 'Volume', 1);

          // Act
          List<SensorType> result = repository.findAllTypes();

          // Assert
          assertEquals("Other", result.get(0).name());
          assertEquals("Stress Radial", result.get(1).name());
          assertEquals("Temperature", result.get(2).name());
          assertEquals("Volume", result.get(3).name());
        });
  }

  @Test
  @Transactional
  void should_stream_unaudited_sensors() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          repository.create(createDummySensor("UNAUDITED-1", "Test", "x"));

          // Act
          try (Stream<Sensor> stream = repository.streamUnauditedSensors()) {
            List<Sensor> unauditedSensors = stream.toList();

            // Assert
            assertFalse(unauditedSensors.isEmpty(), "Stream should not be empty");

            boolean containsOurSensor =
                unauditedSensors.stream().anyMatch(s -> s.getCode().equals("UNAUDITED-1"));

            assertTrue(
                containsOurSensor, "Stream should contain the newly created unaudited sensor");
          }
        });
  }

  @Test
  @Transactional
  void should_exclude_audited_sensors() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // Arrange
          Sensor sensor = repository.create(createDummySensor("AUDITED-1", "Test", "x"));

          javers.commit("TEST_AUTHOR", sensor);

          // Act
          try (Stream<Sensor> stream = repository.streamUnauditedSensors()) {
            List<Sensor> unauditedSensors = stream.toList();

            // Assert
            boolean containsOurSensor =
                unauditedSensors.stream().anyMatch(s -> s.getCode().equals("AUDITED-1"));

            assertFalse(containsOurSensor, "Stream should NOT contain the audited sensor");
          }
        });
  }

  // --- Helper Methods ---

  /**
   * Helper to quickly build a valid domain Sensor for testing.
   */
  private Sensor createDummySensor(String code, String name, String formulaExpression) {
    return createDummySensorWithCoordinates(
        code, name, formulaExpression, new Coordinates(2400, -12007, -1600));
  }

  private Sensor createDummySensorWithCoordinates(
      String code, String name, String formulaExpression, Coordinates coordinates) {
    Formula formula = new Formula();
    formula.setExpression(formulaExpression);
    AlarmLimits alarmLimits = new AlarmLimits(0.0, 100.0);

    return new Sensor(
        code,
        name,
        new SensorType(null, "Other", null),
        Unit.METER,
        null,
        coordinates,
        alarmLimits,
        true,
        formula);
  }

  private int indexOfCode(List<Sensor> rows, String code) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).getCode().equals(code)) {
        return i;
      }
    }
    throw new AssertionError("Expected to find sensor with code " + code);
  }
}
