package ch.swisstopo.monteis.core.modules.measurement.query;

import ch.swisstopo.monteis.core.infrastructure.exception.TooManyExportRowsException;
import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import java.io.IOException;
import java.io.Writer;

/**
 * Read-flow contract for writing all measurement rows (one per sensor parameter, with its latest
 * reading) matching a filter/sort as CSV to a {@link Writer}. Bypasses DTO mapping, the CSV rows
 * are written directly from jOOQ records.
 */
public interface MeasurementCsvExportQueryRepository {

  /**
   * Fetches all matching rows and writes them, or throws {@link TooManyExportRowsException} without
   * writing anything when more rows match than {@code exportRequest.limit()}.
   */
  void writeCsvUnlessTooManyRows(PagedRequest exportRequest, Writer writer) throws IOException;
}
