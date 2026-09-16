package ch.swisstopo.monteis.core.modules.experiment.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentTest {

  private final Period standardPeriod =
      new Period(LocalDate.of(2024, Month.FEBRUARY, 1), LocalDate.of(2025, Month.FEBRUARY, 1));
  private final LocalDate referenceToday = LocalDate.of(2024, Month.JUNE, 15);

  @Test
  void should_initialize_new_experiment_without_id_and_version() {
    // given
    String name = "Test Experiment";
    String owner = "John Doe";
    String description = "This is a new experiment";

    // when
    Experiment experiment = new Experiment(name, owner, standardPeriod, description);

    // then
    assertAll(
        () -> assertEquals(name, experiment.getName(), "Name should be mapped correctly"),
        () -> assertEquals(owner, experiment.getOwner(), "Owner should be mapped correctly"),
        () ->
            assertEquals(
                standardPeriod, experiment.getPeriod(), "Dates should be mapped correctly"),
        () ->
            assertEquals(
                description,
                experiment.getComment(),
                "Description should be mapped to comment field"),
        () -> assertNull(experiment.getId(), "ID should be null for a newly created experiment"),
        () ->
            assertNull(
                experiment.getVersion(), "Version should be null for a newly created experiment"),
        () ->
            assertNull(
                experiment.getSensorCount(),
                "SensorCount should be null for a newly created experiment"));
  }

  @Test
  void should_rebuild_existing_experiment_with_all_fields() {
    // given
    UUID id = UUID.randomUUID();
    String name = "Existing Experiment";
    String description = "This is a rebuilt experiment";
    Integer version = 1;
    Integer sensorCount = 5;

    // when
    Experiment experiment =
        new Experiment(id, name, standardPeriod, description, version, sensorCount);

    // then
    assertAll(
        () -> assertEquals(id, experiment.getId(), "ID should be mapped correctly"),
        () -> assertEquals(name, experiment.getName(), "Name should be mapped correctly"),
        () ->
            assertEquals(
                standardPeriod, experiment.getPeriod(), "Dates should be mapped correctly"),
        () ->
            assertEquals(
                description,
                experiment.getComment(),
                "Description should be mapped to comment field"),
        () -> assertEquals(version, experiment.getVersion(), "Version should be mapped correctly"),
        () ->
            assertEquals(
                sensorCount, experiment.getSensorCount(), "SensorCount should be mapped correctly"),
        () ->
            assertNull(
                experiment.getOwner(), "Owner should be null as it's not in this constructor"));
  }

  @Test
  void should_update_fields_using_setters() {
    // given
    Experiment experiment = new Experiment("Initial", "Owner", standardPeriod, "Desc");
    Period newPeriod =
        new Period(LocalDate.of(2025, Month.JANUARY, 1), LocalDate.of(2025, Month.DECEMBER, 31));

    // when
    UUID updatedId = UUID.randomUUID();
    experiment.setId(updatedId);
    experiment.setName("Updated Name");
    experiment.setOwner("Updated Owner");
    experiment.getStatus(referenceToday);
    experiment.setPeriod(newPeriod);
    experiment.setComment("Updated Comment");
    experiment.setVersion(2);
    experiment.setSensorCount(10);

    // then
    assertAll(
        () -> assertEquals(updatedId, experiment.getId()),
        () -> assertEquals("Updated Name", experiment.getName()),
        () -> assertEquals("Updated Owner", experiment.getOwner()),
        () -> assertEquals(Status.UPCOMING, experiment.getStatus(referenceToday)),
        () -> assertEquals(newPeriod, experiment.getPeriod()),
        () -> assertEquals("Updated Comment", experiment.getComment()),
        () -> assertEquals(2, experiment.getVersion()),
        () -> assertEquals(10, experiment.getSensorCount()));
  }

  @Test
  void should_delegate_status_computation_to_its_period() {
    // given: boundary-case coverage (historic/upcoming/active) lives in PeriodTest - this only
    // confirms Experiment.getStatus delegates to it rather than computing status itself.
    Period historicPeriod =
        new Period(LocalDate.of(2022, Month.JANUARY, 1), LocalDate.of(2023, Month.JANUARY, 1));
    Experiment experiment = new Experiment("Name", "Owner", historicPeriod, "Desc");

    // when
    Status status = experiment.getStatus(referenceToday);

    // then
    assertEquals(historicPeriod.getStatus(referenceToday), status);
  }
}
