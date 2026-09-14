package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.tables.Experiments.EXPERIMENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.query.TextFilterModel;
import ch.swisstopo.monteis.core.itconfig.IT;
import ch.swisstopo.monteis.core.itconfig.SecurityContextTestSupport;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * All test bodies run as admin ({@link SecurityContextTestSupport#runAsAdmin}) - these tests
 * exercise the CSV export query itself, not row-level security.
 *
 * <p>Status is computed in SQL via {@code JooqExperimentRepository.STATUS_FIELD}, against the
 * database's actual current date ({@code CURRENT_DATE}, not an injectable {@code Clock}) - periods
 * here are therefore built relative to {@link LocalDate#now()} rather than fixed dates, so these
 * assertions hold regardless of when the test runs.
 */
@IT
class JooqExperimentCsvExportQueryRepositoryIT {

  @Autowired private DSLContext dsl;

  @Autowired private JooqExperimentCsvExportQueryRepository exportRepository;

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
              "name,status,period.start,period.end,sensorCount,comment,id", lines.getFirst());
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
      exportRepository.streamCsv(request, writer);
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
                .set(EXPERIMENTS.OWNER, "owner")
                .returning(EXPERIMENTS.ID)
                .fetchOne())
        .getId();
  }
}
