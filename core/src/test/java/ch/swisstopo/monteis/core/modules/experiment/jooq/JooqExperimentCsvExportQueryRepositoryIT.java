package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.ExperimentOwner.EXPERIMENT_OWNER;
import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.SetFilterModel;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * All test bodies run as admin ({@link SecurityContextTestSupport#runAsAdmin}) - these tests
 * exercise the CSV export query itself, not row-level security.
 *
 * <p>Status is computed via the domain layer ({@code Period.getStatus}), against the app's
 * injected {@code Clock} bean (which is {@code Clock.systemDefaultZone()} in this Spring context,
 * same as {@link LocalDate#now()}) - periods here are therefore built relative to {@code
 * LocalDate.now()} rather than fixed dates, so these assertions hold regardless of when the test
 * runs.
 */
@IT
class JooqExperimentCsvExportQueryRepositoryIT {

  @Autowired private DSLContext dsl;

  @Autowired private JooqExperimentCsvExportQueryRepository exportRepository;

  /** Stands in for ExperimentOwnerService's resolver, the owner rule is tested there. */
  private Function<StoredOwners, VisibleOwners> owners = _ -> VisibleOwners.NONE;

  private static final DirectoryUser ALICE =
      new DirectoryUser(UUID.randomUUID(), "Alice", "Example", "alice@example.test");
  private static final DirectoryUser BOB =
      new DirectoryUser(UUID.randomUUID(), "Bob", "Builder", "bob@example.test");

  @Test
  @Transactional
  void should_export_the_resolved_owner_names_for_the_stored_owners() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          UUID experimentId =
              createExperiment(
                  "OwnerCsvExportExperiment",
                  null,
                  LocalDate.of(2024, 1, 1),
                  LocalDate.of(2024, 12, 31));
          addOwners(experimentId, ALICE.id(), BOB.id());
          List<StoredOwners> asked = new ArrayList<>();
          owners =
              storedOwners -> {
                asked.add(storedOwners);
                return VisibleOwners.of(List.of(BOB, ALICE));
              };

          String csv = streamToString(nameFilter("OwnerCsvExportExperiment"));

          assertEquals(
              List.of(new StoredOwners(experimentId, Set.of(ALICE.id(), BOB.id()))), asked);
          List<String> lines = List.of(csv.split("\r\n"));
          assertTrue(lines.get(1).contains(",0,Bob Builder; Alice Example,,"), csv);
        });
  }

  @Test
  @Transactional
  void should_mark_the_owners_unavailable_when_keycloak_is_unavailable() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          UUID experimentId =
              createExperiment(
                  "OfflineCsvExportExperiment",
                  null,
                  LocalDate.of(2024, 1, 1),
                  LocalDate.of(2024, 12, 31));
          addOwners(experimentId, ALICE.id());
          owners = _ -> VisibleOwners.UNAVAILABLE;

          String csv = streamToString(nameFilter("OfflineCsvExportExperiment"));

          assertTrue(List.of(csv.split("\r\n")).get(1).contains(",0,(unavailable),"), csv);
        });
  }

  @Test
  @Transactional
  void should_apply_the_owner_filter_to_the_export() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          UUID owned =
              createExperiment(
                  "FilteredCsvOwned", null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
          createExperiment(
              "FilteredCsvOther", null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
          addOwners(owned, ALICE.id());
          owners = _ -> VisibleOwners.of(List.of(ALICE));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "name",
                      new TextFilterModel("contains", "FilteredCsv", null),
                      "owners",
                      new SetFilterModel("set", Set.of(ALICE.id().toString()))));

          List<String> lines = List.of(streamToString(request).split("\r\n"));

          assertEquals(2, lines.size());
          assertTrue(lines.get(1).startsWith("FilteredCsvOwned,"));
        });
  }

  private static PagedRequest nameFilter(String name) {
    return new PagedRequest(
        0, 10, List.of(), Map.of("name", new TextFilterModel("contains", name, null)));
  }

  private void addOwners(UUID experimentId, UUID... ownerIds) {
    for (UUID ownerId : ownerIds) {
      dsl.insertInto(EXPERIMENT_OWNER)
          .set(EXPERIMENT_OWNER.EXPERIMENT_ID, experimentId)
          .set(EXPERIMENT_OWNER.USER_ID, ownerId)
          .execute();
    }
  }

  @Test
  @Transactional
  void should_stream_header_and_rows_matching_the_filter() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: a period spanning today, so status is deterministically ACTIVE
          LocalDate start = LocalDate.now().minusYears(1);
          LocalDate end = LocalDate.now().plusYears(1);
          createExperiment("UniqueCsvExportExperiment", "A comment", start, end);
          createExperiment("Other Experiment", "Another comment", start, end);

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "uniquecsvexport", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(
              "name,status,period.start,period.end,sensorCount,owners,comment,id",
              lines.getFirst());
          assertEquals(2, lines.size(), "Header plus exactly one matching row");
          assertTrue(
              lines
                  .get(1)
                  .startsWith("UniqueCsvExportExperiment,ACTIVE,%s,%s,0,".formatted(start, end)));
        });
  }

  @Test
  @Transactional
  void should_compute_historic_status_for_a_period_entirely_in_the_past() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: a period that ended well before today
          createExperiment(
              "HistoricCsvExportExperiment",
              "Comment",
              LocalDate.now().minusYears(2),
              LocalDate.now().minusYears(1));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "name",
                      new TextFilterModel("contains", "HistoricCsvExportExperiment", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertTrue(
              lines.get(1).startsWith("HistoricCsvExportExperiment,HISTORIC,"),
              "A period entirely in the past must be HISTORIC: " + csv);
        });
  }

  @Test
  @Transactional
  void should_compute_upcoming_status_for_a_period_entirely_in_the_future() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: a period that starts well after today
          createExperiment(
              "UpcomingCsvExportExperiment",
              "Comment",
              LocalDate.now().plusYears(1),
              LocalDate.now().plusYears(2));

          PagedRequest request =
              new PagedRequest(
                  0,
                  10,
                  List.of(),
                  Map.of(
                      "name",
                      new TextFilterModel("contains", "UpcomingCsvExportExperiment", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertTrue(
              lines.get(1).startsWith("UpcomingCsvExportExperiment,UPCOMING,"),
              "A period entirely in the future must be UPCOMING: " + csv);
        });
  }

  @Test
  @Transactional
  void should_cap_rows_at_the_export_requests_limit() {
    SecurityContextTestSupport.runAsAdmin(
        () -> {
          // given: three experiments matching the filter, but the request's limit only allows 2
          createExperiment(
              "CapExperiment One", "c", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
          createExperiment(
              "CapExperiment Two", "c", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
          createExperiment(
              "CapExperiment Three", "c", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

          PagedRequest request =
              new PagedRequest(
                  0,
                  2,
                  List.of(),
                  Map.of("name", new TextFilterModel("contains", "CapExperiment", null)));

          // when
          String csv = streamToString(request);

          // then
          List<String> lines = List.of(csv.split("\r\n"));
          assertEquals(3, lines.size(), "Header plus exactly 2 rows, not all 3 matches");
        });
  }

  private String streamToString(PagedRequest request) {
    StringWriter writer = new StringWriter();
    try {
      exportRepository.streamCsv(request, writer, owners);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  private UUID createExperiment(String name, String comment, LocalDate start, LocalDate end) {
    return Objects.requireNonNull(
            dsl.insertInto(EXPERIMENTS)
                .set(EXPERIMENTS.NAME, name)
                .set(EXPERIMENTS.COMMENT, comment)
                .set(EXPERIMENTS.START, start)
                .set(EXPERIMENTS.END, end)
                .returning(EXPERIMENTS.ID)
                .fetchOne())
        .getId();
  }
}
