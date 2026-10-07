package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.infrastructure.userdirectory.DirectoryUser;
import ch.swisstopo.monteis.core.modules.experiment.domain.Period;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentCsvExportQueryRepository;
import ch.swisstopo.monteis.core.modules.experiment.query.StoredOwners;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;
import java.io.IOException;
import java.io.Writer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
  private static final String OWNERS_UNAVAILABLE = "(unavailable)";

  private final DSLContext dsl;
  private final Clock clock;

  public JooqExperimentCsvExportQueryRepository(DSLContext dsl, Clock clock) {
    this.dsl = dsl;
    this.clock = clock;
  }

  @Override
  public void streamCsv(
      PagedRequest exportRequest, Writer writer, Function<StoredOwners, VisibleOwners> owners)
      throws IOException {
    // Reuses JooqExperimentRepository's colId->Field map so the export honors exactly the same
    // filter/sort semantics as the grid.
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            ExperimentOwnerFilter.withoutOwnerFilter(exportRequest),
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
                JooqExperimentRepository.OWNER_IDS_FIELD,
                EXPERIMENTS.COMMENT,
                EXPERIMENTS.ID)
            .from(EXPERIMENTS)
            .where(criteria.condition().and(ExperimentOwnerFilter.condition(exportRequest)))
            .orderBy(criteria.sortFields())
            .limit(exportRequest.limit())
            .fetchLazy()) {
      for (Record r : cursor) {
        Period period = new Period(r.get(EXPERIMENTS.START), r.get(EXPERIMENTS.END));
        CsvWriter.writeRow(
            writer,
            Arrays.asList(
                r.get(EXPERIMENTS.NAME),
                period.getStatus(today),
                r.get(EXPERIMENTS.START),
                r.get(EXPERIMENTS.END),
                r.get(JooqExperimentRepository.SENSOR_COUNT_FIELD_NAME, Integer.class),
                ownerNames(
                    owners.apply(
                        new StoredOwners(
                            r.get(EXPERIMENTS.ID),
                            Set.of(r.get(JooqExperimentRepository.OWNER_IDS_FIELD))))),
                r.get(EXPERIMENTS.COMMENT),
                r.get(EXPERIMENTS.ID)));
        writer.flush();
      }
    }
  }

  private static String ownerNames(VisibleOwners owners) {
    if (owners.unavailable()) {
      return OWNERS_UNAVAILABLE;
    }
    return owners.users().stream()
        .map(DirectoryUser::displayName)
        .collect(Collectors.joining(OWNER_SEPARATOR));
  }
}
