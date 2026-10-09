package ch.swisstopo.monteis.core.modules.organisation.query;

import ch.swisstopo.monteis.core.infrastructure.query.PagedRequest;
import java.io.IOException;
import java.io.Writer;

/**
 * Read-flow contract for streaming all organisations matching a filter/sort as CSV, straight to a
 * {@link Writer}. Unlike {@code OrganisationRepository#getOrganisations}, this bypasses the Domain
 * layer entirely - CSV rows are written directly from jOOQ records.
 */
public interface OrganisationCsvExportQueryRepository {
  void streamCsv(PagedRequest exportRequest, Writer writer) throws IOException;
}
