import { DatePipe } from '@angular/common';
import { ReadSimpleMetricDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';

export function createColumns(datePipe: DatePipe): TableColumn<ReadSimpleMetricDto>[] {
  return [
    {
      field: 'sensorId',
      headerName: 'Sensor ID',
      sortable: true,
      filter: true,
      flex: 2,
    },
    {
      field: 'timestamp',
      headerName: 'Timestamp',
      sortable: true,
      filter: true,
      flex: 1.5,
      valueFormatter: (params) => datePipe.transform(params.value, 'medium') ?? '',
    },
    {
      field: 'normValue',
      headerName: 'Value (Normalized)',
      sortable: true,
      filter: true,
      flex: 1,
    },
    {
      field: 'rawValue',
      headerName: 'Value (Raw)',
      sortable: true,
      filter: true,
      flex: 1,
    },
  ];
}
