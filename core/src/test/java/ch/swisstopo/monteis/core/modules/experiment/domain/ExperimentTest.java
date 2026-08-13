package ch.swisstopo.monteis.core.modules.experiment.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.Test;

class ExperimentTest {
  Period period =
      new Period(LocalDate.of(2024, Month.FEBRUARY, 1), LocalDate.of(2025, Month.FEBRUARY, 1));

  @Test
  void should_initialize_new_experiment_without_id_and_version() {
    // given
    String name = "Test Experiment";
    String owner = "John Doe";
    Period dates = period;
    String description = "This is a new experiment";

    // when
    Experiment experiment = new Experiment(name, owner, dates, description);

    // then
    assertAll(
        () -> assertEquals(name, experiment.getName(), "Name should be mapped correctly"),
        () -> assertEquals(owner, experiment.getOwner(), "Owner should be mapped correctly"),
        () -> assertEquals(dates, experiment.getPeriod(), "Dates should be mapped correctly"),
        () ->
            assertEquals(
                description,
                experiment.getComment(),
                "Description should be mapped to comment field"),
        () -> assertNull(experiment.getId(), "ID should be null for a newly created experiment"),
        () ->
            assertNull(
                experiment.getVersion(), "Version should be null for a newly created experiment"));
  }

  @Test
  void should_rebuild_existing_experiment_with_all_fields() {
    // given
    Long id = 100L;
    String name = "Existing Experiment";
    String owner = "Jane Doe";
    Period dates = period;
    String description = "This is a rebuilt experiment";
    Integer version = 1;
    Status status = Status.ACTIVE;

    // when
    Experiment experiment = new Experiment(id, name, owner, dates, description, version, status);

    // then
    assertAll(
        () -> assertEquals(id, experiment.getId(), "ID should be mapped correctly"),
        () -> assertEquals(name, experiment.getName(), "Name should be mapped correctly"),
        () -> assertEquals(owner, experiment.getOwner(), "Owner should be mapped correctly"),
        () -> assertEquals(dates, experiment.getPeriod(), "Dates should be mapped correctly"),
        () -> assertEquals(status, experiment.getStatus(), "Status should be mapped correctly"),
        () ->
            assertEquals(
                description,
                experiment.getComment(),
                "Description should be mapped to comment field"),
        () -> assertEquals(version, experiment.getVersion(), "Version should be mapped correctly"));
  }

  @Test
  void should_update_fields_using_setters() {
    // given
    Experiment experiment = new Experiment("Initial", "Owner", period, "Desc");

    // when
    experiment.setId(99L);
    experiment.setName("Updated Name");
    experiment.setOwner("Updated Owner");
    experiment.setComment("Updated Comment");
    experiment.setVersion(2);

    // then
    assertAll(
        () -> assertEquals(99L, experiment.getId()),
        () -> assertEquals("Updated Name", experiment.getName()),
        () -> assertEquals("Updated Owner", experiment.getOwner()),
        () -> assertEquals("Updated Comment", experiment.getComment()),
        () -> assertEquals(2, experiment.getVersion()));
  }
}
