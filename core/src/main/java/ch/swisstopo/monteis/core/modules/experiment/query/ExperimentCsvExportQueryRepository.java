package ch.swisstopo.monteis.core.modules.experiment.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import java.io.IOException;
import java.io.Writer;
import java.util.function.Function;

/**
 * Read-flow contract for streaming all experiments matching a filter/sort as CSV, straight to a
 * {@link Writer}. Unlike {@code ExperimentRepository#getExperiments}, this bypasses the Domain
 * layer entirely - there's no DTO round-trip, since CSV rows are written directly from jOOQ
 * records.
 */
public interface ExperimentCsvExportQueryRepository {
  /**
   * @param owners resolves the owners shown in a row, the owner rule lives with the caller and
   *     needs Keycloak, which a query repository has no business asking
   */
  void streamCsv(
      PagedRequest exportRequest, Writer writer, Function<StoredOwners, VisibleOwners> owners)
      throws IOException;
}
