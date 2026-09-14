import { DatePipe } from '@angular/common';
import { MeasurementResponseDto } from '@core/generated';
import { translate } from '@ngx-translate/core';
import { TableColumn } from '@ui/table/table.types';

export function createColumns(datePipe: DatePipe): TableColumn<MeasurementResponseDto>[] {
  return [
    {
      field: 'dasSensorAlias',
      headerName: translate('measurements-table.column.sensorId')(),
      sortable: true,
      filter: true,
      flex: 1.5,
    },
    {
      field: 'experimentName',
      headerName: translate('measurements-table.column.experimentName')(),
      sortable: true,
      filter: true,
      flex: 1.5,
    },
    {
      field: 'sensorName',
      headerName: translate('measurements-table.column.sensorName')(),
      sortable: true,
      filter: true,
      flex: 1.5,
    },
    {
      field: 'newestMeasurement',
      headerName: translate('measurements-table.column.newestMeasurement')(),
      sortable: true,
      filter: true,
      flex: 1.5,
      valueFormatter: (params) =>
        params.value ? (datePipe.transform(params.value, 'medium') ?? '') : '-',
    },
    {
      field: 'measureValue',
      headerName: translate('measurements-table.column.measureValue')(),
      sortable: true,
      filter: true,
      flex: 1,
      valueFormatter: (params) => (params.value != null ? Number(params.value).toFixed(2) : '-'),
    },
    {
      field: 'unit',
      headerName: translate('measurements-table.column.unit')(),
      sortable: true,
      filter: true,
      flex: 1,
    },
    {
      field: 'sensorType',
      headerName: translate('measurements-table.column.sensorType')(),
      sortable: true,
      filter: true,
      flex: 1,
    },
    {
      field: 'x',
      headerName: translate('measurements-table.column.xyz')(),
      sortable: false,
      filter: false,
      flex: 1.5,
      valueGetter: (params) => {
        const x = params.data?.x ?? '-';
        const y = params.data?.y ?? '-';
        const z = params.data?.z ?? '-';
        return `${x} / ${y} / ${z}`;
      },
    },
    {
      field: 'alarmLimitFrom',
      headerName: translate('measurements-table.column.alarmLimits')(),
      sortable: false,
      filter: false,
      flex: 1.5,
      valueGetter: (params) => {
        const lower = params.data?.alarmLimitFrom ?? '-';
        const upper = params.data?.alarmLimitTo ?? '-';
        return `${lower} to ${upper}`;
      },
    },
    {
      field: 'active',
      headerName: translate('measurements-table.column.active')(),
      sortable: true,
      filter: true,
      flex: 0.5,
      valueFormatter: (params) => (params.value ? 'Active' : 'Inactive'),
    },
    {
      field: 'comment',
      headerName: translate('measurements-table.column.comment')(),
      sortable: true,
      filter: true,
      flex: 2,
    },
  ];
}
