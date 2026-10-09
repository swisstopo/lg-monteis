package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.io.IOException;
import java.io.Writer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streams all experiments matching a filter/sort as CSV, one row at a time, without ever
 * materializing the full result set in memory: the header row, the jOOQ cursor iteration and
 * every row write all happen inside this single {@code @Transactional(readOnly = true)} method,
 * so the DB connection (and the RLS context Postgres enforces against it) stays open for the
 * whole export. This intentionally isn't a {@code StreamingResponseBody} callback - that runs on
 * a separate thread after the controller method (and thus this transaction) has already
 * returned/committed.
 */
@Repository
@Transactional(readOnly = true)
public class JooqExperimentCsvExportQueryRepository implements ExperimentCsvExportQueryRepository {

  private static final List<String> HEADER =
      List.of(
          "name", "status", "period.start", "period.end", "sensorCount", "owners", "comment", "id");
  private static final String OWNER_SEPARATOR = "; ";

  private final DSLContext dsl;
  private final Clock clock;

  public JooqExperimentCsvExportQueryRepository(DSLContext dsl, Clock clock) {
    this.dsl = dsl;
    this.clock = clock;
  }

  @Override
  public void streamCsv(PagedRequest exportRequest, Writer writer, Map<UUID, VisibleOwners> owners)
      throws IOException {
    // Reuses JooqExperimentRepository's colId->Field map so the export honors exactly the same
    // filter/sort semantics as the grid.
    ExperimentOwnerFilter.Split ownerFilter = ExperimentOwnerFilter.split(exportRequest);
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            ownerFilter.requestWithoutOwnersColumn(),
            JooqExperimentRepository.COLUMNS_BY_COL_ID,
            EXPERIMENTS.ID.asc());

    LocalDate today = LocalDate.now(clock);
    CsvWriter.writeRow(writer, HEADER);

    try (var cursor =
        dsl.select(
                EXPERIMENTS.NAME,
                EXPERIMENTS.START,
                EXPERIMENTS.END,
                JooqExperimentRepository.SENSOR_COUNT_FIELD.as(
                    JooqExperimentRepository.SENSOR_COUNT_FIELD_NAME),
                EXPERIMENTS.COMMENT,
                EXPERIMENTS.ID)
            .from(EXPERIMENTS)
            .where(criteria.condition().and(ownerFilter.ownerCondition()))
            .orderBy(criteria.sortFields())
            .limit(exportRequest.limit())
            .fetchLazy()) {
      for (Record r : cursor) {
        CsvWriter.writeRow(writer, rowOf(r, today, owners));
        writer.flush();
      }
    }
  }

  private static List<Object> rowOf(Record r, LocalDate today, Map<UUID, VisibleOwners> owners) {
    Period period = new Period(r.get(EXPERIMENTS.START), r.get(EXPERIMENTS.END));
    VisibleOwners visibleOwners = owners.getOrDefault(r.get(EXPERIMENTS.ID), VisibleOwners.NONE);
    return Arrays.asList(
        r.get(EXPERIMENTS.NAME),
        period.getStatus(today),
        r.get(EXPERIMENTS.START),
        r.get(EXPERIMENTS.END),
        r.get(JooqExperimentRepository.SENSOR_COUNT_FIELD_NAME, Integer.class),
        ownerNames(visibleOwners),
        r.get(EXPERIMENTS.COMMENT),
        r.get(EXPERIMENTS.ID));
  }

  private static String ownerNames(VisibleOwners owners) {
    return switch (owners.status()) {
      case SHOWN -> namesOf(owners.users());
      case KEYCLOAK_UNAVAILABLE -> "(unavailable)";
      case ACCESS_DENIED -> "(access denied)";
      case NO_WRITE_GROUP -> "(no write group)";
    };
  }

  private static String namesOf(List<DirectoryUser> users) {
    return users.stream()
        .map(DirectoryUser::displayName)
        // a ; in a name would break splitting the cell on the separator, csv has no escape for it
        .map(name -> name.replace(";", ""))
        .collect(Collectors.joining(OWNER_SEPARATOR));
  }
}
