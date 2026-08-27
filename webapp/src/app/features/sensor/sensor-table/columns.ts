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
      filter: 'agTextColumnFilter',
    },
    {
      field: 'name',
      headerName: i18n.translate('sensor.name.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'type.name',
      headerName: i18n.translate('sensor.type.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'unit',
      headerName: i18n.translate('sensor.unit.label')(),
      sortable: true,
      // Not text-filterable: the backend column is a DB-level enum, not a plain string.
      filter: false,
      // Show the unit's translated shorthand symbol (e.g. "m", "kg") instead of the raw enum
      // value (e.g. "METER").
      valueFormatter: (params) =>
        unitMetadata[params.value as SensorResponseDto.UnitEnum]?.symbol() ?? '',
    },
    {
      field: 'formula.expression',
      headerName: i18n.translate('sensor.formula.column')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'coordinates.x',
      headerName: i18n.translate('sensor.coordinate.xLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'coordinates.y',
      headerName: i18n.translate('sensor.coordinate.yLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'coordinates.z',
      headerName: i18n.translate('sensor.coordinate.zLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'alarmLimits',
      headerName: i18n.translate('sensor.alarmLimit.column')(),
      minWidth: 150,
      // No single natural sort/filter key: this is a composite of the lower/upper limit fields.
      sortable: false,
      filter: false,
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
      // Not filterable: a boolean Yes/No column would need agSetColumnFilter, a different
      // filter-model shape the backend doesn't translate yet.
      filter: false,
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
      filter: 'agTextColumnFilter',
      wrapText: true,
      autoHeight: true,
      flex: 0,
      width: 500,
      suppressAutoSize: true,
    },
  ];
}
