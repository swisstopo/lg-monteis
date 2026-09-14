package ch.swisstopo.monteis.core.modules.experiment.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.EXPERIMENTS;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.modules.experiment.query.ExperimentCsvExportQueryRepository;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
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
public class JooqExperimentCsvExportQueryRepository implements ExperimentCsvExportQueryRepository {

  private static final List<String> HEADER =
      List.of("name", "status", "period.start", "period.end", "sensorCount", "comment", "id");

  private final DSLContext dsl;

  public JooqExperimentCsvExportQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  @Transactional(readOnly = true)
  public void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException {
    // Reuses JooqExperimentRepository's colId->Field map so the export honors exactly the same
    // filter/sort semantics as the grid.
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            exportRequest, JooqExperimentRepository.COLUMNS_BY_COL_ID, EXPERIMENTS.ID.asc());

    CsvWriter.writeRow(writer, HEADER);

    try (var cursor =
        dsl.select(
                EXPERIMENTS.NAME,
                JooqExperimentRepository.STATUS_FIELD,
                EXPERIMENTS.START,
                EXPERIMENTS.END,
                JooqExperimentRepository.SENSOR_COUNT_FIELD.as(
                    JooqExperimentRepository.SENSOR_COUNT_FIELD_NAME),
                EXPERIMENTS.COMMENT,
                EXPERIMENTS.ID)
            .from(EXPERIMENTS)
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(exportRequest.limit())
            .fetchLazy()) {
      for (Record r : cursor) {
        CsvWriter.writeRow(
            writer,
            Arrays.asList(
                r.get(EXPERIMENTS.NAME),
                r.get(JooqExperimentRepository.STATUS_FIELD),
                r.get(EXPERIMENTS.START),
                r.get(EXPERIMENTS.END),
                r.get(JooqExperimentRepository.SENSOR_COUNT_FIELD_NAME, Integer.class),
                r.get(EXPERIMENTS.COMMENT),
                r.get(EXPERIMENTS.ID)));
        writer.flush();
      }
    }
  }
}
