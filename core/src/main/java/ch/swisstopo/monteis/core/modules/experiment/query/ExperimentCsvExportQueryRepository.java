package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import java.io.IOException;
import java.io.Writer;

/**
 * Read-flow contract for streaming all experiments matching a filter/sort as CSV, straight to a
 * {@link Writer}. Unlike {@code ExperimentRepository#getExperiments}, this bypasses the Domain
 * layer entirely - there's no DTO round-trip, since CSV rows are written directly from jOOQ
 * records.
 */
public interface ExperimentCsvExportQueryRepository {
  void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException;
}
