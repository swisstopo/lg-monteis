import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { SensorResponseDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';
import { getUnitMetadata } from '../models/sensor.model';

export function createColumns(): TableColumn<SensorResponseDto>[] {
  const i18n = inject(TranslateService);
  const unitMetadata = getUnitMetadata();

  return [
    {
      field: 'code',
      headerName: i18n.translate('sensor.code.label')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'name',
      headerName: i18n.translate('sensor.name.label')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'type.name',
      headerName: i18n.translate('sensor.type.label')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'unit',
      headerName: i18n.translate('sensor.unit.label')(),
      sortable: true,
      filter: true,
      // Show the unit's translated shorthand symbol (e.g. "m", "kg") instead of the raw enum
      // value (e.g. "METER").
      valueFormatter: (params) =>
        unitMetadata[params.value as SensorResponseDto.UnitEnum]?.symbol() ?? '',
    },
    {
      field: 'formula.expression',
      headerName: i18n.translate('sensor.formula.column')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.x',
      headerName: i18n.translate('sensor.coordinate.xLocal.label')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.y',
      headerName: i18n.translate('sensor.coordinate.yLocal.label')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'coordinates.z',
      headerName: i18n.translate('sensor.coordinate.zLocal.label')(),
      sortable: true,
      filter: true,
    },
    {
      field: 'alarmLimits',
      headerName: i18n.translate('sensor.alarmLimit.column')(),
      minWidth: 150,
      valueFormatter: (params) => {
        const alarmLimits = params.value;
        return alarmLimits
          ? i18n.translate('sensor.alarmLimit.display', {
              lower: alarmLimits.lower,
              upper: alarmLimits.upper,
            })()
          : '';
      },
    },
    {
      field: 'active',
      headerName: i18n.translate('sensor.active.label')(),
      sortable: true,
      filter: true,
      // Without this, ag-grid infers the boolean cell data type from the row data and renders a
      // (valueFormatter-ignoring) checkbox instead of the Yes/No text below.
      cellDataType: false,
      valueFormatter: (params) =>
        params.value
          ? i18n.translate('sensor.active.column.yes')()
          : i18n.translate('sensor.active.column.no')(),
    },
    {
      field: 'comment',
      headerName: i18n.translate('sensor.comment.label')(),
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
