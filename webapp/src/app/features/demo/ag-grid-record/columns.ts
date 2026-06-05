import { DatePipe } from '@angular/common';
import { ReadSimpleMetric } from '../../../core/generated/model/readSimpleMetric';
import { TableColumn } from '../../../ui/table/table.types';

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
      sortable: true,
      filter: true,
      flex: 1,
    },
  ];
}
