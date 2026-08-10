import { WritableSignal } from '@angular/core';
import { IDatasource, IGetRowsParams } from 'ag-grid-community';

/**
 * Builds an ag-grid IDatasource for the infinite row model, wiring the standard
 * success/error bookkeeping (totalCount + loadError signals) around a feature's
 * own paged-fetch call. Shared by every feature table using this pattern
 * (sensor, experiment, ...).
 */
export function createPagedDatasource<T>(
  fetchPage: (params: IGetRowsParams) => Promise<{ rows?: T[]; totalCount?: number }>,
  totalCount: WritableSignal<number | undefined>,
  loadError: WritableSignal<boolean>,
): IDatasource {
  return {
    getRows: (params) => {
      fetchPage(params).then(
        (result) => {
          loadError.set(false);
          totalCount.set(result.totalCount);
          params.successCallback(result.rows ?? [], result.totalCount ?? 0);
        },
        () => {
          loadError.set(true);
          params.failCallback();
        },
      );
    },
  };
}
