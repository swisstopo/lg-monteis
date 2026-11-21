import { Params } from '@angular/router';
import { FilterModel } from 'ag-grid-community';

export const TABLE_FILTER_PARAM = 'filter';

export function tableFilterParams(filterModel: FilterModel = {}): Params {
  return Object.keys(filterModel).length > 0
    ? { [TABLE_FILTER_PARAM]: JSON.stringify(filterModel) }
    : {};
}

export function tableLink(path: string, filterModel: FilterModel = {}): unknown[] {
  return [path, tableFilterParams(filterModel)];
}
