package ch.swisstopo.monteis.core.infrastructure.query;

/**
 * The flat, query-parameter-bindable shape of a CSV export request: just {@code sortModel}/{@code
 * filterModel} (JSON strings, see {@link RawPagedRequest}) - unlike a paged grid request, an
 * export has no client-driven {@code startRow}/{@code endRow}; the row cap is server-side and
 * configured. Use {@link PagedRequestParser#parseForExport(RawExportRequest)} to turn this into a
 * {@link PagedRequest}.
 */
public record RawExportRequest(String sortModel, String filterModel) {}
