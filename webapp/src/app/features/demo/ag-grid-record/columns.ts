import { TableColumn } from '../../../ui/table/table.types';
import { ReadSimpleMetric } from '../../../core/generated/model/readSimpleMetric';
import { DatePipe } from '@angular/common';

export function createColumns(datePipe: DatePipe): TableColumn<ReadSimpleMetric>[] {
  return [
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
      field: 'val',
      header: 'Value',
      type: 'number',
      sortable: true,
      filter: true,
      flex: 1,
    },
  ];
}
