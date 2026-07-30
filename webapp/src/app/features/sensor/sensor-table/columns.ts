import { SensorResponseDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';

export function createColumns(): TableColumn<SensorResponseDto>[] {
  return [
    {
      field: 'code',
      headerName: 'Code',
      sortable: true,
      filter: true,
    },
    {
      field: 'name',
      headerName: 'Name',
      sortable: true,
      filter: true,
    },
    {
      field: 'type.name',
      headerName: 'Type',
      sortable: true,
      filter: true,
    },
    {
      field: 'unit',
      headerName: 'Unit',
      sortable: true,
      filter: true,
    },
    {
      field: 'formula.expression',
      headerName: 'Formula',
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.x',
      headerName: 'X (Local)',
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.y',
      headerName: 'Y (Local)',
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.z',
      headerName: 'Z (Local)',
      sortable: true,
      filter: true,
    },
    {
      field: 'alarmLimits',
      headerName: 'Alarm Limits',
      minWidth: 150,
      valueFormatter: (params) => {
        const alarmLimits = params.value;
        return alarmLimits ? `[${alarmLimits.lower} to ${alarmLimits.upper}]` : '';
      },
    },
    {
      field: 'active',
      headerName: 'Active',
      sortable: true,
      filter: true,
      // Without this, ag-grid infers the boolean cell data type from the row data and renders a
      // (valueFormatter-ignoring) checkbox instead of the Yes/No text below.
      cellDataType: false,
      valueFormatter: (params) => (params.value ? 'Yes' : 'No'),
    },
    {
      field: 'comment',
      headerName: 'Comment',
      sortable: true,
      filter: true,
      wrapText: true,
      autoHeight: true,
      flex: 0,
      width: 500,
      suppressAutoSize: true,
    },
  ];
}
