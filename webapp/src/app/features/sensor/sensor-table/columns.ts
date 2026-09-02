import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { SensorResponseDto } from '../../../core/generated';
import { TableColumn } from '../../../ui/table/table.types';
import { getUnitMetadata } from '../models/sensor.model';

export function createColumns(): TableColumn<SensorResponseDto>[] {
  const translateService = inject(TranslateService);
  const unitMetadata = getUnitMetadata();

  return [
    {
      field: 'code',
      headerName: translateService.translate('sensor.code.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'name',
      headerName: translateService.translate('sensor.name.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'type.name',
      headerName: translateService.translate('sensor.type.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'unit',
      headerName: translateService.translate('sensor.unit.label')(),
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
      headerName: translateService.translate('sensor.formula.column')(),
      sortable: true,
      filter: 'agTextColumnFilter',
    },
    {
      field: 'coordinates.x',
      headerName: translateService.translate('sensor.coordinate.xLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'coordinates.y',
      headerName: translateService.translate('sensor.coordinate.yLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'coordinates.z',
      headerName: translateService.translate('sensor.coordinate.zLocal.label')(),
      sortable: true,
      filter: 'agNumberColumnFilter',
    },
    {
      field: 'alarmLimits',
      headerName: translateService.translate('sensor.alarmLimit.column')(),
      minWidth: 150,
      // No single natural sort/filter key: this is a composite of the lower/upper limit fields.
      sortable: false,
      filter: false,
      valueFormatter: (params) => {
        const alarmLimits = params.value;
        return alarmLimits
          ? translateService.translate('sensor.alarmLimit.display', {
              lower: alarmLimits.lower,
              upper: alarmLimits.upper,
            })()
          : '';
      },
    },
    {
      field: 'active',
      headerName: translateService.translate('sensor.active.label')(),
      sortable: true,
      // Not filterable: a boolean Yes/No column would need agSetColumnFilter, a different
      // filter-model shape the backend doesn't translate yet.
      filter: false,
      // Without this, ag-grid infers the boolean cell data type from the row data and renders a
      // (valueFormatter-ignoring) checkbox instead of the Yes/No text below.
      cellDataType: false,
      valueFormatter: (params) =>
        params.value
          ? translateService.translate('sensor.active.column.yes')()
          : translateService.translate('sensor.active.column.no')(),
    },
    {
      field: 'comment',
      headerName: translateService.translate('sensor.comment.label')(),
      sortable: true,
      filter: 'agTextColumnFilter',
      // Variable/auto row height isn't supported by the infinite row model this table uses (a
      // long comment would render past the fixed row height and bleed into the row below), so
      // this stays single-line and truncates with an ellipsis; the full text is available via
      // the tooltip.
      tooltipField: 'comment',
      flex: 0,
      width: 500,
      suppressAutoSize: true,
    },
  ];
}
