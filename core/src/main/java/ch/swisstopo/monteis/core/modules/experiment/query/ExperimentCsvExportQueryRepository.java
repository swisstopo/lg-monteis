package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.UUID;

/**
 * Read-flow contract for streaming all experiments matching a filter/sort as CSV, straight to a
 * {@link Writer}. Unlike {@code ExperimentRepository#getExperiments}, this bypasses the Domain
 * layer entirely - there's no DTO round-trip, since CSV rows are written directly from jOOQ
 * records.
 */
public interface ExperimentCsvExportQueryRepository {
  /**
   * @param owners the visible owners per experiment, resolved before streaming; an experiment
   *     missing here has none
   */
  void streamCsv(PagedRequest exportRequest, Writer writer, Map<UUID, VisibleOwners> owners)
      throws IOException;
}
