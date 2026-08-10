import { IGetRowsParams } from 'ag-grid-community';

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
