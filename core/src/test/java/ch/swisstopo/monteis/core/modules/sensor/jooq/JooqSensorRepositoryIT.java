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
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.modules.sensor.domain.Bounds;
import ch.swisstopo.monteis.core.modules.sensor.domain.Formula;
import ch.swisstopo.monteis.core.modules.sensor.domain.Sensor;
import ch.swisstopo.monteis.core.modules.sensor.web.dto.outbound.FormulaResponseDto;
import java.util.List;
import java.util.stream.Stream;
import org.javers.core.Javers;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IT
class JooqSensorRepositoryIT {
  @Autowired private JooqSensorRepository repository;

  @Autowired private DSLContext dsl;

  @Autowired private Javers javers;

  @Test
  @Transactional
  void should_create_sensor_and_formula() {
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
        () -> assertNotNull(savedSensor.getFormula().getId(), "Formula ID should not be null"),
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
  }

  @Test
  @Transactional
  void should_reuse_existing_formula() {
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
        sensorCountAfterFirst + 1, dsl.fetchCount(SENSORS), "A second sensor should be inserted");
    assertEquals(
        formulaCountAfterFirst, dsl.fetchCount(FORMULAS), "No new formula should be inserted");
  }

  @Test
  @Transactional
  void should_throw_on_duplicate_code() {
    // Arrange
    Sensor sensor1 = createDummySensor("DUPE-CODE", "Sensor 1", "x");
    Sensor sensor2 = createDummySensor("DUPE-CODE", "Sensor 2", "x+y");
    repository.create(sensor1);

    // Act & Assert
    FieldBusinessValidationException exception =
        assertThrows(FieldBusinessValidationException.class, () -> repository.create(sensor2));

    assertEquals("code", exception.getField());
    assertEquals("validation.unique", exception.getMessageKey());
  }

  @Test
  @Transactional
  void should_update_sensor() {
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
  }

  @Test
  @Transactional
  void should_throw_on_update_deleted_sensor() {
    // Arrange
    Sensor ghostSensor = createDummySensor("GHOST", "Ghost", "x");
    ghostSensor.setId(9999L);

    // Act & Assert
    ObjectBusinessValidationException exception =
        assertThrows(ObjectBusinessValidationException.class, () -> repository.update(ghostSensor));

    assertEquals("object.deleted", exception.getMessageKey());
  }

  @Test
  @Transactional
  void should_throw_on_update_duplicated_code() {
    // Arrange
    Sensor sensor1 = createDummySensor("UPD-DUPE-CODE", "Sensor 1", "x");
    Sensor sensor2 = createDummySensor("UPD-NO-DUPE-CODE", "Sensor 2", "x+y");
    sensor1 = repository.create(sensor1);
    sensor2 = repository.create(sensor2);

    sensor2.setCode(sensor1.getCode());

    // Act & Assert
    Sensor finalSensor = sensor2;
    FieldBusinessValidationException exception =
        assertThrows(FieldBusinessValidationException.class, () -> repository.update(finalSensor));

    assertEquals("code", exception.getField());
    assertEquals("validation.unique", exception.getMessageKey());
  }

  @Test
  @Transactional
  void should_find_all_formulas_ordered() {
    // Arrange
    repository.create(createDummySensor("S-01", "A", "a * x"));
    repository.create(createDummySensor("S-02", "B", "b * x"));
    repository.create(createDummySensor("S-03", "C", "c * x"));

    // Act
    List<FormulaResponseDto> formulas = repository.findAllFormulas();

    // Assert
    assertEquals("a * x", formulas.get(0).expression());
    assertEquals("b * x", formulas.get(1).expression());
    assertEquals("c * x", formulas.get(2).expression());
  }

  @Test
  @Transactional
  void should_stream_unaudited_sensors() {
    // Arrange
    repository.create(createDummySensor("UNAUDITED-1", "Test", "x"));

    // Act
    try (Stream<Sensor> stream = repository.streamUnauditedSensors()) {
      List<Sensor> unauditedSensors = stream.toList();

      // Assert
      assertFalse(unauditedSensors.isEmpty(), "Stream should not be empty");

      boolean containsOurSensor =
          unauditedSensors.stream().anyMatch(s -> s.getCode().equals("UNAUDITED-1"));

      assertTrue(containsOurSensor, "Stream should contain the newly created unaudited sensor");
    }
  }

  @Test
  @Transactional
  void should_exclude_audited_sensors() {
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
  }

  // --- Helper Methods ---

  /**
   * Helper to quickly build a valid domain Sensor for testing.
   */
  private Sensor createDummySensor(String code, String name, String formulaExpression) {
    Formula formula = new Formula();
    formula.setExpression(formulaExpression);
    Bounds bounds = new Bounds(0.0, 100.0);

    return new Sensor(code, name, bounds, formula);
  }
}
