package ch.swisstopo.monteis.core.modules.experiment.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.Month;
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
                "SensorCount should be null for a newly created experiment"),
        () -> assertNull(experiment.getStatus(), "Status should be null initially"));
  }

  @Test
  void should_rebuild_existing_experiment_with_all_fields() {
    // given
    Long id = 100L;
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
    Period newPeriod = new Period(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

    // when
    experiment.setId(99L);
    experiment.setName("Updated Name");
    experiment.setOwner("Updated Owner");
    experiment.setStatus(Status.ACTIVE);
    experiment.setPeriod(newPeriod);
    experiment.setComment("Updated Comment");
    experiment.setVersion(2);
    experiment.setSensorCount(10);

    // then
    assertAll(
        () -> assertEquals(99L, experiment.getId()),
        () -> assertEquals("Updated Name", experiment.getName()),
        () -> assertEquals("Updated Owner", experiment.getOwner()),
        () -> assertEquals(Status.ACTIVE, experiment.getStatus()),
        () -> assertEquals(newPeriod, experiment.getPeriod()),
        () -> assertEquals("Updated Comment", experiment.getComment()),
        () -> assertEquals(2, experiment.getVersion()),
        () -> assertEquals(10, experiment.getSensorCount()));
  }

  @Test
  void should_set_status_to_historic_when_end_date_is_before_today() {
    // given
    Period historicPeriod = new Period(LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1));
    Experiment experiment = new Experiment("Name", "Owner", historicPeriod, "Desc");

    // when
    experiment.calculateAndSetStatus(referenceToday);

    // then
    assertEquals(Status.HISTORIC, experiment.getStatus());
  }

  @Test
  void should_set_status_to_upcoming_when_start_date_is_after_today() {
    // given
    Period upcomingPeriod = new Period(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
    Experiment experiment = new Experiment("Name", "Owner", upcomingPeriod, "Desc");

    // when
    experiment.calculateAndSetStatus(referenceToday);

    // then
    assertEquals(Status.UPCOMING, experiment.getStatus());
  }

  @Test
  void should_set_status_to_active_when_today_is_between_start_and_end_dates() {
    // given
    Period activePeriod = new Period(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
    Experiment experiment = new Experiment("Name", "Owner", activePeriod, "Desc");

    // when
    experiment.calculateAndSetStatus(referenceToday); // reference is 2024-06-15

    // then
    assertEquals(Status.ACTIVE, experiment.getStatus());
  }

  @Test
  void should_set_status_to_active_when_today_is_exactly_start_or_end_date() {
    // given
    LocalDate start = LocalDate.of(2024, 6, 15);
    LocalDate end = LocalDate.of(2024, 12, 15);
    Period period = new Period(start, end);
    Experiment experiment = new Experiment("Name", "Owner", period, "Desc");

    // when (testing start boundary)
    experiment.calculateAndSetStatus(start);
    // then
    assertEquals(Status.ACTIVE, experiment.getStatus(), "Should be active exactly on start date");

    // when (testing end boundary)
    experiment.calculateAndSetStatus(end);
    // then
    assertEquals(Status.ACTIVE, experiment.getStatus(), "Should be active exactly on end date");
  }
}
