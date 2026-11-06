package ch.swisstopo.monteis.core.modules.measurement.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import java.io.IOException;
import java.io.Writer;

/**
 * Read-flow contract for streaming all measurement rows (one per sensor parameter, with its latest
 * reading) matching a filter/sort as CSV, straight to a {@link Writer}. Bypasses DTO mapping, the
 * CSV rows are written directly from jOOQ records.
 */
public interface MeasurementCsvExportQueryRepository {
  void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException;
}
