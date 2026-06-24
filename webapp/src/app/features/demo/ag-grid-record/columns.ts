import { DatePipe } from '@angular/common';
import { ReadSimpleMetric } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';

export function createColumns(datePipe: DatePipe): TableColumn<ReadSimpleMetric>[] {
  return [
    {
      field: 'sensorId',
      header: 'Sensor ID',
      sortable: true,
      filter: true,
      resizable: true,
      flex: 1,
    },
    {
      field: 'timestamp',
      header: 'Timestamp',
      sortable: true,
      filter: true,
      resizable: true,
      flex: 2,
      valueFormatter: (params) => datePipe.transform(params.value, 'medium') ?? '',
    },
    {
      field: 'normValue',
      header: 'Value (Normalized)',
      sortable: true,
      filter: true,
      flex: 1,
    },
    {
      field: 'rawValue',
      header: 'Value (Raw)',
      sortable: true,
      filter: true,
      flex: 1,
    },
  ];
}
