import { tableLink } from '@ui/table/table-link';
import { FilterModel } from 'ag-grid-community';

export function measurementsTableLink(filterModel: FilterModel = {}): unknown[] {
  return tableLink('/measurements-table', filterModel);
}

export function dasKeyFilterModel(dasKey: string): FilterModel {
  return { dasKey: { filterType: 'text', type: 'equals', filter: dasKey } };
}
