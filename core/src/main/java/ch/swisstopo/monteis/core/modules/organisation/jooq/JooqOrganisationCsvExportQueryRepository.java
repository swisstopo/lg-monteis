package ch.swisstopo.monteis.core.modules.organisation.jooq;

import static ch.swisstopo.monteis.core.jooq.generated.Tables.ORGANISATIONS;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.infrastructure.jooq.PagedRequestJooqTranslator;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import ch.swisstopo.monteis.core.modules.organisation.query.OrganisationCsvExportQueryRepository;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streams all organisations matching a filter/sort as CSV, one row at a time, without ever
 * materializing the full result set in memory. Header, cursor iteration and row writes all happen
 * inside this single read-only transaction, so the DB connection stays open for the whole export.
 */
@Repository
@Transactional(readOnly = true)
public class JooqOrganisationCsvExportQueryRepository
    implements OrganisationCsvExportQueryRepository {

  private static final List<String> HEADER = List.of("name", "comment", "id");

  private final DSLContext dsl;

  public JooqOrganisationCsvExportQueryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException {
    // Same colId->Field map as the grid, so the export honors the same filter/sort semantics.
    PagedRequestJooqTranslator.JooqPageCriteria criteria =
        PagedRequestJooqTranslator.translate(
            exportRequest, JooqOrganisationRepository.COLUMNS_BY_COL_ID, ORGANISATIONS.NAME.asc());

    CsvWriter.writeRow(writer, HEADER);

    try (var cursor =
        dsl.select(ORGANISATIONS.NAME, ORGANISATIONS.COMMENT, ORGANISATIONS.ID)
            .from(ORGANISATIONS)
            .where(criteria.condition())
            .orderBy(criteria.sortFields())
            .limit(exportRequest.limit())
            .fetchLazy()) {
      for (Record r : cursor) {
        CsvWriter.writeRow(
            writer,
            List.of(
                r.get(ORGANISATIONS.NAME), r.get(ORGANISATIONS.COMMENT), r.get(ORGANISATIONS.ID)));
        writer.flush();
      }
    }
  }
}
