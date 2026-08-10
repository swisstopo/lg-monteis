package ch.swisstopo.monteis.core.infrastructure.query;

/**
 * The flat, query-parameter-bindable shape of a {@link PagedRequest}: {@code sortModel} and
 * {@code filterModel} arrive as JSON strings (ag-grid's own {@code sortModel}/{@code filterModel}
 * shapes are a list of objects and a dynamic nested map, which don't bind cleanly as plain GET
 * query parameters). Use {@link PagedRequestParser} to turn this into a {@link PagedRequest}.
 */
public record RawPagedRequest(int startRow, int endRow, String sortModel, String filterModel) {}
