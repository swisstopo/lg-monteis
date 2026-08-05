import { DatePipe } from '@angular/common';
import { translate } from '@ngx-translate/core';
import { ReadSimpleMetricDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';

export function createColumns(datePipe: DatePipe): TableColumn<ReadSimpleMetricDto>[] {
  return [
    {
      field: 'sensorId',
      headerName: translate('measurements.column.sensorId')(),
      sortable: true,
      filter: true,
      flex: 2,
    },
    {
      field: 'timestamp',
      headerName: translate('measurements.column.timestamp')(),
      sortable: true,
      filter: true,
      flex: 1.5,
      valueFormatter: (params) => datePipe.transform(params.value, 'medium') ?? '',
    },
    {
      field: 'normValue',
      headerName: translate('measurements.column.normValue')(),
      sortable: true,
      filter: true,
      flex: 1,
    },
    {
      field: 'rawValue',
      headerName: translate('measurements.column.rawValue')(),
      sortable: true,
      filter: true,
      flex: 1,
    },
  ];
}
