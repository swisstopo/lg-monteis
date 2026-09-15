import { GridApi, IGetRowsParams } from 'ag-grid-community';

/**
 * Converts ag-grid's infinite-row-model IGetRowsParams into the flat query params the backend's
 * PagedRequestParser expects (sortModel/filterModel are sent as JSON strings, since ag-grid's own
 * shapes - a list of objects and a dynamic nested map - don't bind cleanly as plain GET params).
 * Shared by every feature table using the infinite row model (sensor, experiment, ...).
 */
export function toPagedRequestParams(params: IGetRowsParams) {
  return {
    startRow: params.startRow,
    endRow: params.endRow,
    sortModel: params.sortModel?.length ? JSON.stringify(params.sortModel) : undefined,
    filterModel:
      params.filterModel && Object.keys(params.filterModel).length
        ? JSON.stringify(params.filterModel)
        : undefined,
  };
}

/**
 * Same sortModel/filterModel shape as {@link toPagedRequestParams}, but read directly off a live
 * GridApi instead of an IGetRowsParams - the infinite row model's datasource only sees the
 * filter/sort model inside its own getRows callback, so a CSV export triggered from a toolbar
 * button (outside that callback) needs this to read "whatever's currently applied" instead.
 */
export function toGridFilterSortParams(gridApi: GridApi) {
  const sortModel = gridApi
    .getColumnState()
    .filter((column) => column.sort != null)
    .sort((a, b) => (a.sortIndex ?? 0) - (b.sortIndex ?? 0))
    .map((column) => ({ colId: column.colId, sort: column.sort! }));
  const filterModel = gridApi.getFilterModel();

  return {
    sortModel: sortModel.length ? JSON.stringify(sortModel) : undefined,
    filterModel: Object.keys(filterModel).length ? JSON.stringify(filterModel) : undefined,
  };
}
