import { translate } from '@ngx-translate/core';
import { SensorResponseDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';
import { UNIT_METADATA } from '../models/sensor.model';

export function createColumns(): TableColumn<SensorResponseDto>[] {
  return [
    {
      field: 'code',
      headerName: translate('sensor.table.column.code')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'name',
      headerName: translate('sensor.table.column.name')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'type.name',
      headerName: translate('sensor.table.column.type')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'unit',
      headerName: translate('sensor.table.column.unit')(),
      sortable: true,
      filter: true,
      // Show the unit's shorthand symbol (e.g. "m", "kg") from the same metadata used elsewhere,
      // instead of the raw enum value (e.g. "METER").
      valueFormatter: (params) =>
        UNIT_METADATA[params.value as SensorResponseDto.UnitEnum]?.symbol ?? '',
    },
    {
      field: 'formula.expression',
      headerName: translate('sensor.table.column.formula')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.x',
      headerName: translate('sensor.table.column.xLocal')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.y',
      headerName: translate('sensor.table.column.yLocal')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.z',
      headerName: translate('sensor.table.column.zLocal')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'alarmLimits',
      headerName: translate('sensor.table.column.alarmLimits')(),
      minWidth: 150,
      valueFormatter: (params) => {
        const alarmLimits = params.value;
        return alarmLimits ? `[${alarmLimits.lower} to ${alarmLimits.upper}]` : '';
      },
    },
    {
      field: 'active',
      headerName: translate('sensor.table.column.active')(),
      sortable: true,
      filter: true,
      // Without this, ag-grid infers the boolean cell data type from the row data and renders a
      // (valueFormatter-ignoring) checkbox instead of the Yes/No text below.
      cellDataType: false,
      valueFormatter: (params) => (params.value ? 'Yes' : 'No'),
    },
    {
      field: 'comment',
      headerName: translate('sensor.table.column.comment')(),
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
