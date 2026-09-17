import { DatePipe } from '@angular/common';
import { ReadSimpleMetricDto } from '@core/generated';
import { translate } from '@ngx-translate/core';
import { MinCharsTextFilter } from '@ui/filters/min-chars-text-filter/min-chars-text-filter';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(datePipe: DatePipe): TableColumn<ReadSimpleMetricDto>[] {
  return [
    {
      field: 'dasKey',
      headerName: translate('measurements-table.column.dasKey')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      // Searched as you type, but only from MIN_SEARCH_CHARS on - see MinCharsTextFilter.
      floatingFilterComponent: MinCharsTextFilter,
      flex: 2,
    },
    {
      field: 'timestamp',
      headerName: translate('measurements-table.column.timestamp')(),
      sortable: true,
      // Day-granular on purpose: the backend compares the reading's timestamp cast to a date.
      filter: 'agDateColumnFilter',
      flex: 1.5,
      valueFormatter: (params) => datePipe.transform(params.value, 'medium') ?? '',
    },
    {
      field: 'normValue',
      headerName: translate('measurements-table.column.normValue')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
    },
    {
      field: 'rawValue',
      headerName: translate('measurements-table.column.rawValue')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
      flex: 1,
    },
  ];
}
